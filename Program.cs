using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Npgsql;
using SplashDash.Api.Data;
using System.Text;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();

var connectionString = builder.Configuration.GetConnectionString("DefaultConnection");
var databaseUrl = Environment.GetEnvironmentVariable("DATABASE_URL");
if (!string.IsNullOrEmpty(databaseUrl))
{
    try
    {
        var databaseUri = new Uri(databaseUrl);
        var userInfo = databaseUri.UserInfo.Split(':');
        connectionString = $"Server={databaseUri.Host};Port={databaseUri.Port};Database={databaseUri.LocalPath.TrimStart('/')};User Id={userInfo[0]};Password={userInfo[1]};SslMode=Require;Trust Server Certificate=true;";
    }
    catch
    {
        // Fallback to raw string if parsing fails
        connectionString = databaseUrl;
    }
}

builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseNpgsql(connectionString));

builder.Services.AddOpenApi();

builder.Services.AddCors(options =>
{
    options.AddPolicy("SplashDashWebsite", policy =>
    {
        policy.WithOrigins(
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "http://localhost:5501",
                "http://127.0.0.1:5501")
            .AllowAnyHeader()
            .AllowAnyMethod();
    });
});

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

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseCors("SplashDashWebsite");

app.UseAuthentication();
app.UseAuthorization();

app.UseHttpsRedirection();

app.MapControllers();

// --- Database Auto-Initializer ---
using (var scope = app.Services.CreateScope())
{
    try
    {
        var builderConn = new NpgsqlConnectionStringBuilder(connectionString);
        var targetDb = builderConn.Database;
        builderConn.Database = "postgres";

        using (var connection = new NpgsqlConnection(builderConn.ToString()))
        {
            connection.Open();
            using var checkCmd = new NpgsqlCommand($"SELECT 1 FROM pg_database WHERE datname = '{targetDb}'", connection);
            var exists = checkCmd.ExecuteScalar();
            
            if (exists == null)
            {
                using var createCmd = new NpgsqlCommand($"CREATE DATABASE \"{targetDb}\"", connection);
                createCmd.ExecuteNonQuery();
            }
        }

        using (var dbConnection = new NpgsqlConnection(connectionString))
        {
            dbConnection.Open();
            string scriptPath = Path.Combine(app.Environment.ContentRootPath, "schema.sql");
            if (File.Exists(scriptPath))
            {
                string script = File.ReadAllText(scriptPath);
                using var scriptCmd = new NpgsqlCommand(script, dbConnection);
                scriptCmd.ExecuteNonQuery();
            }
        }
    }
    catch (Exception ex)
    {
        Console.WriteLine($"Database initialization note: {ex.Message}");
    }
}
// ---------------------------------

app.Run();