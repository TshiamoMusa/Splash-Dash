
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SplashDash.Api.Data;
using SplashDash.Api.Models;
using System.Data;
using System.Security.Claims;
using System.Text.RegularExpressions;

namespace SplashDash.Api.Controllers;

[ApiController]
[Route("api/staff")]
[Authorize(Roles = "Admin")]
public class StaffController : ControllerBase
{
    private const int MaximumSuperAdmins = 3;

    private readonly AppDbContext _context;
    private readonly PasswordHasher<User> _passwordHasher = new();

    public StaffController(AppDbContext context)
    {
        _context = context;
    }

    private async Task<User?> GetCurrentAdminAsync()
    {
        string? idValue = User.FindFirstValue(ClaimTypes.NameIdentifier);

        if (!int.TryParse(idValue, out int userId))
            return null;

        return await _context.Users.FirstOrDefaultAsync(u =>
            u.UserId == userId &&
            u.AccountType == "Admin" &&
            u.IsActive);
    }

    private bool TryFormatAndValidateName(string input, out string formattedName)
    {
        formattedName = string.Empty;

        if (string.IsNullOrWhiteSpace(input))
            return false;

        string[] parts = input.Trim().Split(
            ' ',
            StringSplitOptions.RemoveEmptyEntries);

        var formattedParts = new List<string>();

        foreach (var part in parts)
        {
            if (part.Length < 3 || !part.All(char.IsLetter))
                return false;

            formattedParts.Add(
                char.ToUpper(part[0]) + part.Substring(1).ToLower());
        }

        formattedName = string.Join(" ", formattedParts);
        return true;
    }

    private bool IsValidUsername(string username)
    {
        return !string.IsNullOrWhiteSpace(username) &&
               username.Length >= 3 &&
               Regex.IsMatch(username.Trim(), @"^[a-zA-Z0-9_\-]+$");
    }

    private bool IsValidPhoneNumber(string phone)
    {
        return !string.IsNullOrWhiteSpace(phone) &&
               Regex.IsMatch(phone.Trim(), @"^\+?[0-9\s\-]{8,15}$");
    }

    private bool IsStrongPassword(string password)
    {
        if (string.IsNullOrWhiteSpace(password) || password.Length < 8)
            return false;

        return password.Any(char.IsUpper) &&
               password.Any(char.IsLower) &&
               password.Any(char.IsDigit);
    }

    // GET: api/staff/all
    [HttpGet("all")]
    public async Task<IActionResult> GetAllAccounts()
    {
        var accounts = await _context.Users
            .Where(u => u.AccountType == "Staff" || u.AccountType == "Admin")
            .OrderBy(u => u.FirstName)
            .Select(u => new
            {
                accountId = u.UserId,
                firstName = u.FirstName,
                surname = u.Surname,
                fullName = u.FirstName + " " + u.Surname,
                email = u.Email,
                phoneNumber = u.PhoneNumber,
                username = u.Username,
                role = u.IsSuperAdmin ? "Super Admin" : u.AccountType,
                accountType = u.AccountType,
                isSuperAdmin = u.IsSuperAdmin,
                profileId = u.AccountType == "Staff"
                    ? (int?)u.StaffProfile!.StaffId
                    : u.AdminProfile!.AdminId,
                isActive = u.IsActive,
                accountStatus = u.IsActive ? "Active" : "Inactive"
            })
            .ToListAsync();

        return Ok(accounts);
    }

    // POST: api/staff
    [HttpPost]
    public async Task<IActionResult> CreateAccount(
        CreateStaffAccountRequest request)
    {
        var currentAdmin = await GetCurrentAdminAsync();

        if (currentAdmin == null)
            return Unauthorized();

        if (string.IsNullOrWhiteSpace(request.FirstName) ||
            string.IsNullOrWhiteSpace(request.Surname) ||
            string.IsNullOrWhiteSpace(request.Email) ||
            string.IsNullOrWhiteSpace(request.PhoneNumber) ||
            string.IsNullOrWhiteSpace(request.Username) ||
            string.IsNullOrWhiteSpace(request.Role) ||
            string.IsNullOrWhiteSpace(request.Password))
        {
            return BadRequest(new { message = "All fields are required." });
        }

        if (!TryFormatAndValidateName(request.FirstName, out string firstName))
        {
            return BadRequest(new
            {
                message = "First name must contain only letters, with at least 3 letters per word."
            });
        }

        if (!TryFormatAndValidateName(request.Surname, out string surname))
        {
            return BadRequest(new
            {
                message = "Surname must contain only letters, with at least 3 letters per word."
            });
        }

        if (!IsValidUsername(request.Username))
        {
            return BadRequest(new
            {
                message = "Username must be at least 3 characters and contain only letters, numbers, underscores or hyphens."
            });
        }

        if (!IsValidPhoneNumber(request.PhoneNumber))
        {
            return BadRequest(new
            {
                message = "Please enter a valid phone number."
            });
        }

        if (!IsStrongPassword(request.Password))
        {
            return BadRequest(new
            {
                message = "Password must be at least 8 characters and include uppercase, lowercase and a number."
            });
        }

        string role = request.Role.Trim();

        if (role != "Staff" &&
            role != "Admin" &&
            role != "SuperAdmin")
        {
            return BadRequest(new
            {
                message = "Role must be Staff, Admin or SuperAdmin."
            });
        }

        if (role == "SuperAdmin" && !currentAdmin.IsSuperAdmin)
        {
            return Forbid();
        }

        string email = request.Email.Trim().ToLowerInvariant();
        string username = request.Username.Trim();

        if (await _context.Users.AnyAsync(u => u.Email.ToLower() == email))
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

        await using var transaction =
            await _context.Database.BeginTransactionAsync(
                IsolationLevel.Serializable);

        if (role == "SuperAdmin")
        {
            int superAdminCount = await _context.Users.CountAsync(
                u => u.AccountType == "Admin" && u.IsSuperAdmin);

            if (superAdminCount >= MaximumSuperAdmins)
            {
                return Conflict(new
                {
                    message = "The maximum of 3 Super Admin accounts has been reached."
                });
            }
        }

        string accountType = role == "Staff" ? "Staff" : "Admin";

        var user = new User
        {
            FirstName = firstName,
            Surname = surname,
            Email = email,
            PhoneNumber = request.PhoneNumber.Trim(),
            Username = username,
            AccountType = accountType,
            IsSuperAdmin = role == "SuperAdmin",
            IsActive = true,
            CreatedAt = DateTimeOffset.UtcNow
        };

        user.PasswordHash = _passwordHasher.HashPassword(
            user,
            request.Password);

        _context.Users.Add(user);

        if (accountType == "Staff")
        {
            _context.Staff.Add(new Staff
            {
                User = user,
                Gender = "Unspecified"
            });
        }
        else
        {
            _context.Admins.Add(new Admin
            {
                User = user
            });
        }

        await _context.SaveChangesAsync();
        await transaction.CommitAsync();

        return Ok(new
        {
            message = role == "SuperAdmin"
                ? "Super Admin account created successfully."
                : role + " account created successfully.",
            accountId = user.UserId,
            firstName = user.FirstName,
            surname = user.Surname,
            email = user.Email,
            username = user.Username,
            role = user.IsSuperAdmin ? "Super Admin" : user.AccountType,
            accountType = user.AccountType,
            isSuperAdmin = user.IsSuperAdmin,
            isActive = user.IsActive
        });
    }

    // PUT: api/staff/{id}/super-admin
    [HttpPut("{id:int}/super-admin")]
    public async Task<IActionResult> UpdateSuperAdminStatus(
        int id,
        UpdateSuperAdminRequest request)
    {
        var currentAdmin = await GetCurrentAdminAsync();

        if (currentAdmin == null)
            return Unauthorized();

        if (!currentAdmin.IsSuperAdmin)
            return Forbid();

        var account = await _context.Users.FirstOrDefaultAsync(u =>
            u.UserId == id && u.AccountType == "Admin");

        if (account == null)
        {
            return NotFound(new
            {
                message = "Admin account not found."
            });
        }

        if (account.UserId == currentAdmin.UserId &&
            !request.IsSuperAdmin)
        {
            return BadRequest(new
            {
                message = "You cannot remove your own Super Admin access."
            });
        }

        if (account.IsSuperAdmin == request.IsSuperAdmin)
        {
            return Ok(new
            {
                message = "The account already has the selected access.",
                isSuperAdmin = account.IsSuperAdmin
            });
        }

        await using var transaction =
            await _context.Database.BeginTransactionAsync(
                IsolationLevel.Serializable);

        if (request.IsSuperAdmin)
        {
            int superAdminCount = await _context.Users.CountAsync(
                u => u.AccountType == "Admin" && u.IsSuperAdmin);

            if (superAdminCount >= MaximumSuperAdmins)
            {
                return Conflict(new
                {
                    message = "The maximum of 3 Super Admin accounts has been reached."
                });
            }
        }
        else if (account.IsActive)
        {
            int activeSuperAdmins = await _context.Users.CountAsync(
                u => u.AccountType == "Admin" &&
                     u.IsSuperAdmin &&
                     u.IsActive);

            if (activeSuperAdmins <= 1)
            {
                return BadRequest(new
                {
                    message = "You cannot remove access from the last active Super Admin."
                });
            }
        }

        account.IsSuperAdmin = request.IsSuperAdmin;

        await _context.SaveChangesAsync();
        await transaction.CommitAsync();

        return Ok(new
        {
            message = request.IsSuperAdmin
                ? "Account promoted to Super Admin successfully."
                : "Super Admin access removed successfully.",
            accountId = account.UserId,
            isSuperAdmin = account.IsSuperAdmin,
            role = account.IsSuperAdmin ? "Super Admin" : "Admin"
        });
    }

    // PUT: api/staff/{id}/status
    [HttpPut("{id:int}/status")]
    public async Task<IActionResult> UpdateAccountStatus(
        int id,
        UpdateStaffStatusRequest request)
    {
        var currentAdmin = await GetCurrentAdminAsync();

        if (currentAdmin == null)
            return Unauthorized();

        var account = await _context.Users.FirstOrDefaultAsync(u =>
            u.UserId == id &&
            (u.AccountType == "Staff" || u.AccountType == "Admin"));

        if (account == null)
        {
            return NotFound(new
            {
                message = "Staff or Admin account not found."
            });
        }

        if (account.AccountType == "Admin" &&
            !currentAdmin.IsSuperAdmin)
        {
            return Forbid();
        }

        if (account.UserId == currentAdmin.UserId && !request.IsActive)
        {
            return BadRequest(new
            {
                message = "You cannot deactivate your own account."
            });
        }

        if (account.IsSuperAdmin && !request.IsActive)
        {
            int activeSuperAdmins = await _context.Users.CountAsync(
                u => u.AccountType == "Admin" &&
                     u.IsSuperAdmin &&
                     u.IsActive);

            if (activeSuperAdmins <= 1)
            {
                return BadRequest(new
                {
                    message = "You cannot deactivate the last active Super Admin."
                });
            }
        }

        account.IsActive = request.IsActive;

        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = request.IsActive
                ? "Account activated successfully."
                : "Account deactivated successfully.",
            accountId = account.UserId,
            isActive = account.IsActive,
            accountStatus = account.IsActive ? "Active" : "Inactive"
        });
    }

    // PUT: api/staff/{id}/password
    [HttpPut("{id:int}/password")]
    public async Task<IActionResult> ResetAccountPassword(
        int id,
        ResetStaffPasswordRequest request)
    {
        var currentAdmin = await GetCurrentAdminAsync();

        if (currentAdmin == null)
            return Unauthorized();

        if (string.IsNullOrWhiteSpace(request.NewPassword))
        {
            return BadRequest(new
            {
                message = "A new password is required."
            });
        }

        if (!IsStrongPassword(request.NewPassword))
        {
            return BadRequest(new
            {
                message = "Password must be at least 8 characters and include uppercase, lowercase and a number."
            });
        }

        var account = await _context.Users.FirstOrDefaultAsync(u =>
            u.UserId == id &&
            (u.AccountType == "Staff" || u.AccountType == "Admin"));

        if (account == null)
        {
            return NotFound(new
            {
                message = "Staff or Admin account not found."
            });
        }

        if (account.AccountType == "Admin" &&
            !currentAdmin.IsSuperAdmin)
        {
            return Forbid();
        }

        account.PasswordHash = _passwordHasher.HashPassword(
            account,
            request.NewPassword);

        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = "Password reset successfully.",
            accountId = account.UserId
        });
    }
}

public class CreateStaffAccountRequest
{
    public string FirstName { get; set; } = string.Empty;
    public string Surname { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string PhoneNumber { get; set; } = string.Empty;
    public string Username { get; set; } = string.Empty;
    public string Role { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}

public class UpdateStaffStatusRequest
{
    public bool IsActive { get; set; }
}

public class ResetStaffPasswordRequest
{
    public string NewPassword { get; set; } = string.Empty;
}

public class UpdateSuperAdminRequest
{
    public bool IsSuperAdmin { get; set; }
}
