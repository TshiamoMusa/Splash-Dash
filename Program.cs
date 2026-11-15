using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Npgsql;
using SplashDash.Api.Data;
using System.Text;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();

// ============================================================
// DATABASE
// DATABASE_URL (set on the host, e.g. Render) takes priority over
// the local appsettings connection string.
// ============================================================
var databaseUrl = Environment.GetEnvironmentVariable("DATABASE_URL");

var rawConnectionString = !string.IsNullOrWhiteSpace(databaseUrl)
    ? databaseUrl
    : builder.Configuration.GetConnectionString("DefaultConnection");

var connectionString = ConvertNeonUrlToNpgsqlConnectionString(rawConnectionString);

if (string.IsNullOrWhiteSpace(connectionString))
{
    throw new InvalidOperationException(
        "No database connection string is configured. " +
        "Set DATABASE_URL or ConnectionStrings:DefaultConnection.");
}

builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseNpgsql(connectionString, npgsqlOptions =>
    {
        // Free-tier hosts and serverless databases drop connections while
        // idle, so transient failures are retried automatically.
        npgsqlOptions.EnableRetryOnFailure(
            maxRetryCount: 5,
            maxRetryDelay: TimeSpan.FromSeconds(10),
            errorCodesToAdd: null);
    }));

builder.Services.AddOpenApi();

// ============================================================
// CORS
// Browsers only allow the website to call this API if the website's
// origin is listed here. Local Live Server origins are always allowed.
// The deployed website is added WITHOUT code changes through the
// "Cors__ExtraOrigins" environment variable on the host, e.g.
//     https://yourname.github.io,https://splashdash.example.com
// (scheme + host only: no path and no trailing slash)
// ============================================================
var allowedOrigins = new List<string>
{
    "http://localhost:5500",
    "http://127.0.0.1:5500",
    "http://localhost:5501",
    "http://127.0.0.1:5501"
};

var extraOrigins = builder.Configuration["Cors:ExtraOrigins"];

if (!string.IsNullOrWhiteSpace(extraOrigins))
{
    allowedOrigins.AddRange(
        extraOrigins
            .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Select(origin => origin.TrimEnd('/')));
}

builder.Services.AddCors(options =>
{
    options.AddPolicy("SplashDashWebsite", policy =>
    {
        policy.WithOrigins(allowedOrigins.ToArray())
            .AllowAnyHeader()
            .AllowAnyMethod();
    });
});

// ============================================================
// AUTHENTICATION (JWT)
// ============================================================
var jwtKey = builder.Configuration["Jwt:Key"]
    ?? throw new InvalidOperationException("JWT signing key is not configured.");

var jwtIssuer = builder.Configuration["Jwt:Issuer"]
    ?? throw new InvalidOperationException("JWT issuer is not configured.");

var jwtAudience = builder.Configuration["Jwt:Audience"]
    ?? throw new InvalidOperationException("JWT audience is not configured.");

builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(jwtKey)),
            ValidateIssuer = true,
            ValidIssuer = jwtIssuer,
            ValidateAudience = true,
            ValidAudience = jwtAudience,
            ValidateLifetime = true,
            ClockSkew = TimeSpan.FromMinutes(1),
            NameClaimType = System.Security.Claims.ClaimTypes.Name,
            RoleClaimType = System.Security.Claims.ClaimTypes.Role
        };
    });

builder.Services.AddAuthorization();

var app = builder.Build();

// ============================================================
// REQUEST PIPELINE
// Order matters: CORS must run before authentication so that
// browser pre-flight (OPTIONS) requests are answered correctly.
// ============================================================
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();

    // Production hosts (e.g. Render) terminate HTTPS at their proxy and
    // forward plain HTTP, so redirecting there can break pre-flight
    // requests. The redirect is only needed for local development.
    app.UseHttpsRedirection();
}

app.UseCors("SplashDashWebsite");

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

// Simple public endpoints. "/health" is useful for host health checks and
// for "waking up" a sleeping free-tier server before staff log in.
app.MapGet("/", () => Results.Ok(new
{
    service = "Splash & Dash API",
    status = "running"
}));

app.MapGet("/health", () => Results.Ok(new { status = "ok" }));

// ============================================================
// DATABASE INITIALISER
// Runs schema.sql on startup. The script is idempotent (safe to run on
// every start), so it can also add columns introduced after the first
// deployment. Make sure schema.sql is copied to the publish output
// (see the .csproj note in the delivery message).
// ============================================================
var logger = app.Services.GetRequiredService<ILogger<Program>>();

try
{
    string scriptPath = Path.Combine(app.Environment.ContentRootPath, "schema.sql");

    if (File.Exists(scriptPath))
    {
        await using var dbConnection = new NpgsqlConnection(connectionString);
        await dbConnection.OpenAsync();

        string script = await File.ReadAllTextAsync(scriptPath);

        await using var scriptCommand = new NpgsqlCommand(script, dbConnection);
        await scriptCommand.ExecuteNonQueryAsync();

        logger.LogInformation("Database schema is up to date.");
    }
    else
    {
        logger.LogWarning(
            "schema.sql was not found at {Path}. The database schema was not checked.",
            scriptPath);
    }
}
catch (Exception ex)
{
    // The API still starts so the problem can be diagnosed from the logs.
    logger.LogError(ex, "Database initialisation failed.");
}

app.Run();

// Converts a postgres:// style URL (Neon, Render, Heroku) into an
// Npgsql connection string. Standard connection strings pass through.
string ConvertNeonUrlToNpgsqlConnectionString(string? url)
{
    if (string.IsNullOrWhiteSpace(url)) return string.Empty;

    if (!url.StartsWith("postgres://") && !url.StartsWith("postgresql://"))
    {
        return url;
    }

    try
    {
        var uri = new Uri(url);
        var userInfo = uri.UserInfo.Split(':');

        var user = userInfo.Length > 0 ? Uri.UnescapeDataString(userInfo[0]) : "";
        var pass = userInfo.Length > 1 ? Uri.UnescapeDataString(userInfo[1]) : "";
        var host = uri.Host;
        var port = uri.Port > 0 ? uri.Port : 5432;
        var db = uri.LocalPath.TrimStart('/');

        return $"Host={host};Port={port};Database={db};Username={user};Password={pass};" +
               "SslMode=Require;Trust Server Certificate=true;Pooling=true;Keepalive=30;";
    }
    catch
    {
        return url;
    }
}