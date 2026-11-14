
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using SplashDash.Api.Data;
using SplashDash.Api.Models;
using System.ComponentModel.DataAnnotations;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;

namespace SplashDash.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly AppDbContext _context;
    private readonly IConfiguration _configuration;
    private readonly IWebHostEnvironment _environment;
    private readonly PasswordHasher<User> _passwordHasher = new();

    public AuthController(
        AppDbContext context,
        IConfiguration configuration,
        IWebHostEnvironment environment)
    {
        _context = context;
        _configuration = configuration;
        _environment = environment;
    }

    // Login for Customers, Staff and Admins.
    [AllowAnonymous]
    [HttpPost("login")]
    public async Task<IActionResult> Login(LoginRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Identifier) ||
            string.IsNullOrWhiteSpace(request.Password))
        {
            return BadRequest(new
            {
                message = "Username/email and password are required."
            });
        }

        string identifier = request.Identifier.Trim().ToLowerInvariant();

        var account = await _context.Users.FirstOrDefaultAsync(u =>
            u.Email.ToLower() == identifier ||
            (u.AccountType != "Customer" &&
             u.Username != null &&
             u.Username.ToLower() == identifier));

        if (account == null || !account.IsActive ||
            string.IsNullOrWhiteSpace(account.PasswordHash))
        {
            return Unauthorized(new
            {
                message = "Invalid username/email or password."
            });
        }

        var result = _passwordHasher.VerifyHashedPassword(
            account,
            account.PasswordHash,
            request.Password);

        if (result == PasswordVerificationResult.Failed)
        {
            return Unauthorized(new
            {
                message = "Invalid username/email or password."
            });
        }

        bool profileExists = account.AccountType switch
        {
            "Admin" => await _context.Admins.AnyAsync(
                a => a.UserId == account.UserId),

            "Staff" => await _context.Staff.AnyAsync(
                s => s.UserId == account.UserId),

            "Customer" => await _context.Customers.AnyAsync(
                c => c.UserId == account.UserId),

            _ => false
        };

        if (!profileExists)
        {
            return Unauthorized(new
            {
                message = "Account profile could not be verified."
            });
        }

        return Ok(CreateLoginResponse(account));
    }

    // Creates the first Admin account.
    [AllowAnonymous]
    [HttpPost("bootstrap-admin")]
    public async Task<IActionResult> BootstrapAdmin(
        BootstrapAdminRequest request)
    {
        // Secure or disable this endpoint after initial setup.

        string? configuredKey = _configuration["Bootstrap:AdminKey"];
        string? suppliedKey = Request.Headers["X-Bootstrap-Key"].FirstOrDefault();

        if (string.IsNullOrWhiteSpace(configuredKey) ||
            string.IsNullOrWhiteSpace(suppliedKey))
        {
            return Unauthorized(new
            {
                message = "Invalid bootstrap credentials."
            });
        }

        bool validKey = CryptographicOperations.FixedTimeEquals(
            Encoding.UTF8.GetBytes(configuredKey),
            Encoding.UTF8.GetBytes(suppliedKey));

        if (!validKey)
        {
            return Unauthorized(new
            {
                message = "Invalid bootstrap credentials."
            });
        }

        if (string.IsNullOrWhiteSpace(request.FirstName) ||
            string.IsNullOrWhiteSpace(request.Surname) ||
            string.IsNullOrWhiteSpace(request.Email) ||
            string.IsNullOrWhiteSpace(request.PhoneNumber) ||
            string.IsNullOrWhiteSpace(request.Username) ||
            string.IsNullOrWhiteSpace(request.Password))
        {
            return BadRequest(new
            {
                message = "All Admin account fields are required."
            });
        }

        if (request.Password.Length < 12)
        {
            return BadRequest(new
            {
                message = "Password must contain at least 12 characters."
            });
        }

        if (await _context.Admins.AnyAsync())
        {
            return Conflict(new
            {
                message = "An Admin account already exists."
            });
        }

        string email = request.Email.Trim().ToLowerInvariant();
        string username = request.Username.Trim();

        if (await _context.Users.AnyAsync(u =>
            u.Email.ToLower() == email))
        {
            return Conflict(new
            {
                message = "An account with this email already exists."
            });
        }

        if (await _context.Users.AnyAsync(u =>
            u.Username != null &&
            u.Username.ToLower() == username.ToLower()))
        {
            return Conflict(new
            {
                message = "This username is already in use."
            });
        }

        var adminUser = new User
        {
            FirstName = request.FirstName.Trim(),
            Surname = request.Surname.Trim(),
            Email = email,
            PhoneNumber = request.PhoneNumber.Trim(),
            Username = username,
            AccountType = "Admin",
            IsSuperAdmin = true,
            IsActive = true,
            CreatedAt = DateTimeOffset.UtcNow
        };

        adminUser.PasswordHash = _passwordHasher.HashPassword(
            adminUser,
            request.Password);

        _context.Users.Add(adminUser);

        _context.Admins.Add(new Admin
        {
            User = adminUser
        });

        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = "Initial Super Admin account created successfully.",
            account = new
            {
                id = adminUser.UserId,
                adminUser.FirstName,
                adminUser.Surname,
                adminUser.Email,
                adminUser.Username,
                role = adminUser.AccountType,
                isSuperAdmin = adminUser.IsSuperAdmin,
                displayRole = "Super Admin"
            }
        });
    }

    // Retrieves the logged-in Staff or Admin profile.
    [Authorize(Roles = "Staff,Admin")]
    [HttpGet("profile")]
    public async Task<IActionResult> GetProfile()
    {
        var account = await GetCurrentPortalAccountAsync();

        if (account == null)
        {
            return Unauthorized(new
            {
                message = "Your account could not be verified."
            });
        }

        return Ok(new
        {
            account = new
            {
                id = account.UserId,
                firstName = account.FirstName,
                surname = account.Surname,
                fullName = $"{account.FirstName} {account.Surname}",
                email = account.Email,
                phoneNumber = account.PhoneNumber,
                username = account.Username,
                role = account.AccountType,
                isSuperAdmin = account.IsSuperAdmin,
                displayRole = account.IsSuperAdmin
                    ? "Super Admin"
                    : account.AccountType
            }
        });
    }

    // Updates the logged-in Staff or Admin profile.
    [Authorize(Roles = "Staff,Admin")]
    [HttpPut("profile")]
    public async Task<IActionResult> UpdateProfile(
        UpdateProfileRequest request)
    {
        var account = await GetCurrentPortalAccountAsync();

        if (account == null)
        {
            return Unauthorized(new
            {
                message = "Your account could not be verified."
            });
        }

        if (string.IsNullOrWhiteSpace(request.FirstName) ||
            string.IsNullOrWhiteSpace(request.Surname) ||
            string.IsNullOrWhiteSpace(request.Email) ||
            string.IsNullOrWhiteSpace(request.PhoneNumber) ||
            string.IsNullOrWhiteSpace(request.Username))
        {
            return BadRequest(new
            {
                message = "All profile fields are required."
            });
        }

        string firstName = request.FirstName.Trim();
        string surname = request.Surname.Trim();
        string email = request.Email.Trim().ToLowerInvariant();
        string phoneNumber = request.PhoneNumber.Trim();
        string username = request.Username.Trim();

        if (!new EmailAddressAttribute().IsValid(email))
        {
            return BadRequest(new
            {
                message = "Please enter a valid email address."
            });
        }

        bool emailExists = await _context.Users.AnyAsync(u =>
            u.UserId != account.UserId &&
            u.Email.ToLower() == email);

        if (emailExists)
        {
            return Conflict(new
            {
                message = "An account with this email address already exists."
            });
        }

        bool usernameExists = await _context.Users.AnyAsync(u =>
            u.UserId != account.UserId &&
            u.Username != null &&
            u.Username.ToLower() == username.ToLower());

        if (usernameExists)
        {
            return Conflict(new
            {
                message = "This username is already in use."
            });
        }

        account.FirstName = firstName;
        account.Surname = surname;
        account.Email = email;
        account.PhoneNumber = phoneNumber;
        account.Username = username;

        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = "Profile updated successfully.",
            account = new
            {
                id = account.UserId,
                firstName = account.FirstName,
                surname = account.Surname,
                fullName = $"{account.FirstName} {account.Surname}",
                email = account.Email,
                phoneNumber = account.PhoneNumber,
                username = account.Username,
                role = account.AccountType,
                isSuperAdmin = account.IsSuperAdmin,
                displayRole = account.IsSuperAdmin
                    ? "Super Admin"
                    : account.AccountType
            }
        });
    }

    // Changes the password of an authenticated Staff or Admin account.
    [Authorize(Roles = "Staff,Admin")]
    [HttpPost("change-password")]
    public async Task<IActionResult> ChangePassword(
        ChangePasswordRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.CurrentPassword) ||
            string.IsNullOrWhiteSpace(request.NewPassword))
        {
            return BadRequest(new
            {
                message = "Both passwords are required."
            });
        }

        if (request.NewPassword.Length < 8)
        {
            return BadRequest(new
            {
                message = "New password must contain at least 8 characters."
            });
        }

        if (request.NewPassword == request.CurrentPassword)
        {
            return BadRequest(new
            {
                message = "Your new password must be different."
            });
        }

        var account = await GetCurrentPortalAccountAsync();

        if (account == null)
        {
            return Unauthorized(new
            {
                message = "Your account could not be verified."
            });
        }

        var verification = _passwordHasher.VerifyHashedPassword(
            account,
            account.PasswordHash ?? string.Empty,
            request.CurrentPassword);

        if (verification == PasswordVerificationResult.Failed)
        {
            return BadRequest(new
            {
                message = "Current password is incorrect."
            });
        }

        account.PasswordHash = _passwordHasher.HashPassword(
            account,
            request.NewPassword);

        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = "Password changed successfully."
        });
    }

    // Finds the account belonging to the authenticated portal user.
    private async Task<User?> GetCurrentPortalAccountAsync()
    {
        string? idValue = User.FindFirstValue(
            ClaimTypes.NameIdentifier);

        string? role = User.FindFirstValue(ClaimTypes.Role);

        if (!int.TryParse(idValue, out int userId) ||
            (role != "Staff" && role != "Admin"))
        {
            return null;
        }

        var account = await _context.Users.FirstOrDefaultAsync(
            u => u.UserId == userId);

        if (account == null ||
            !account.IsActive ||
            account.AccountType != role)
        {
            return null;
        }

        return account;
    }

    private object CreateLoginResponse(User account)
    {
        string key = _configuration["Jwt:Key"]
            ?? throw new InvalidOperationException(
                "JWT signing key is not configured.");

        string issuer = _configuration["Jwt:Issuer"]
            ?? throw new InvalidOperationException(
                "JWT issuer is not configured.");

        string audience = _configuration["Jwt:Audience"]
            ?? throw new InvalidOperationException(
                "JWT audience is not configured.");

        var now = DateTime.UtcNow;
        var expires = now.AddHours(8);

        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, account.UserId.ToString()),
            new(ClaimTypes.Name,
                $"{account.FirstName} {account.Surname}"),
            new(ClaimTypes.Email, account.Email),
            new(ClaimTypes.Role, account.AccountType),
            new("is_super_admin",
                account.IsSuperAdmin ? "true" : "false")
        };

        var signingKey = new SymmetricSecurityKey(
            Encoding.UTF8.GetBytes(key));

        var credentials = new SigningCredentials(
            signingKey,
            SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            issuer: issuer,
            audience: audience,
            claims: claims,
            notBefore: now,
            expires: expires,
            signingCredentials: credentials);

        return new
        {
            message = "Login successful.",
            token = new JwtSecurityTokenHandler().WriteToken(token),
            tokenType = "Bearer",
            expiresAt = expires,
            account = new
            {
                id = account.UserId,
                firstName = account.FirstName,
                surname = account.Surname,
                fullName = $"{account.FirstName} {account.Surname}",
                email = account.Email,
                role = account.AccountType,
                isSuperAdmin = account.IsSuperAdmin,
                displayRole = account.IsSuperAdmin
                    ? "Super Admin"
                    : account.AccountType
            }
        };
    }

    // Google login / sync for Customers.
    [AllowAnonymous]
    [HttpPost("google-login")]
    public async Task<IActionResult> GoogleLogin(GoogleLoginRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Email))
        {
            return BadRequest(new
            {
                message = "Email is required for Google login."
            });
        }

        string email = request.Email.Trim().ToLowerInvariant();

        var account = await _context.Users.FirstOrDefaultAsync(
            u => u.Email.ToLower() == email);

        if (account == null)
        {
            string fullName = string.IsNullOrWhiteSpace(request.Name)
                ? "Customer User"
                : request.Name.Trim();

            string firstName = fullName;
            string surname = "Customer";

            int lastSpace = fullName.LastIndexOf(' ');

            if (lastSpace > 0)
            {
                firstName = fullName.Substring(0, lastSpace);
                surname = fullName.Substring(lastSpace + 1);
            }

            account = new User
            {
                FirstName = firstName,
                Surname = surname,
                Email = email,
                PhoneNumber = request.PhoneNumber ?? string.Empty,
                FirebaseUid = request.FirebaseUid,
                AccountType = "Customer",
                IsActive = true,
                CreatedAt = DateTimeOffset.UtcNow
            };

            var customer = new Customer
            {
                User = account,
                HomeAddress = request.Address ?? string.Empty
            };

            _context.Users.Add(account);
            _context.Customers.Add(customer);

            await _context.SaveChangesAsync();
        }
        else
        {
            if (!string.IsNullOrWhiteSpace(request.FirebaseUid))
            {
                account.FirebaseUid = request.FirebaseUid;
                await _context.SaveChangesAsync();
            }
        }

        return Ok(CreateLoginResponse(account));
    }
}

public class LoginRequest
{
    public string Identifier { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}

public class GoogleLoginRequest
{
    public string Email { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string? PhoneNumber { get; set; }
    public string? Address { get; set; }
    public string? FirebaseUid { get; set; }
}

public class ChangePasswordRequest
{
    public string CurrentPassword { get; set; } = string.Empty;
    public string NewPassword { get; set; } = string.Empty;
}

public class UpdateProfileRequest
{
    public string FirstName { get; set; } = string.Empty;
    public string Surname { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string PhoneNumber { get; set; } = string.Empty;
    public string Username { get; set; } = string.Empty;
}

public class BootstrapAdminRequest
{
    public string FirstName { get; set; } = string.Empty;
    public string Surname { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string PhoneNumber { get; set; } = string.Empty;
    public string Username { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}
