
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SplashDash.Api.Data;
using SplashDash.Api.Models;
using System.Security.Claims;

namespace SplashDash.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize(Roles = "Admin")]
public class StaffController : ControllerBase
{
    private readonly AppDbContext _context;
    private readonly PasswordHasher<User> _passwordHasher = new();

    public StaffController(AppDbContext context)
    {
        _context = context;
    }

    // GET: api/Staff/all
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
                role = u.AccountType,
                profileId = u.AccountType == "Staff"
                    ? (int?)u.StaffProfile!.StaffId
                    : u.AdminProfile!.AdminId,
                isActive = u.IsActive,
                accountStatus = u.IsActive ? "Active" : "Inactive"
            })
            .ToListAsync();

        return Ok(accounts);
    }

    // POST: api/Staff
    [HttpPost]
    public async Task<IActionResult> CreateAccount(CreateStaffAccountRequest request)
    {
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

        if (request.Password.Length < 8)
        {
            return BadRequest(new
            {
                message = "Temporary password must contain at least 8 characters."
            });
        }

        string role = request.Role.Trim();

        if (role != "Staff" && role != "Admin")
        {
            return BadRequest(new { message = "Role must be Staff or Admin." });
        }

        string email = request.Email.Trim().ToLowerInvariant();
        string username = request.Username.Trim();

        if (await _context.Users.AnyAsync(u => u.Email.ToLower() == email))
        {
            return Conflict(new { message = "An account with this email already exists." });
        }

        if (await _context.Users.AnyAsync(u =>
            u.Username != null &&
            u.Username.ToLower() == username.ToLower()))
        {
            return Conflict(new { message = "This username is already in use." });
        }

        var user = new User
        {
            FirstName = request.FirstName.Trim(),
            Surname = request.Surname.Trim(),
            Email = email,
            PhoneNumber = request.PhoneNumber.Trim(),
            Username = username,
            AccountType = role,
            IsActive = true,
            CreatedAt = DateTimeOffset.UtcNow
        };

        user.PasswordHash = _passwordHasher.HashPassword(user, request.Password);

        _context.Users.Add(user);

        if (role == "Staff")
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

        return CreatedAtAction(
            nameof(GetAllAccounts),
            new
            {
                accountId = user.UserId,
                role = user.AccountType
            },
            new
            {
                message = role + " account created successfully.",
                accountId = user.UserId,
                firstName = user.FirstName,
                surname = user.Surname,
                email = user.Email,
                username = user.Username,
                role = user.AccountType,
                isActive = user.IsActive
            });
    }

    // PUT: api/Staff/{id}/status
    [HttpPut("{id:int}/status")]
    public async Task<IActionResult> UpdateAccountStatus(
        int id,
        UpdateStaffStatusRequest request)
    {
        var account = await _context.Users.FirstOrDefaultAsync(u =>
            u.UserId == id &&
            (u.AccountType == "Staff" || u.AccountType == "Admin"));

        if (account == null)
        {
            return NotFound(new { message = "Staff or Admin account not found." });
        }

        if (!request.IsActive && account.AccountType == "Admin")
        {
            string? currentUserId = User.FindFirstValue(ClaimTypes.NameIdentifier);

            if (int.TryParse(currentUserId, out int loggedInUserId) &&
                loggedInUserId == account.UserId)
            {
                return BadRequest(new
                {
                    message = "You cannot deactivate your own Admin account."
                });
            }

            int activeAdmins = await _context.Admins
                .CountAsync(a => a.User != null && a.User.IsActive);

            if (activeAdmins <= 1)
            {
                return BadRequest(new
                {
                    message = "The last active Admin account cannot be deactivated."
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

    // PUT: api/Staff/{id}/password
    [HttpPut("{id:int}/password")]
    public async Task<IActionResult> ResetAccountPassword(
        int id,
        ResetStaffPasswordRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.NewPassword))
        {
            return BadRequest(new { message = "A new password is required." });
        }

        if (request.NewPassword.Length < 8)
        {
            return BadRequest(new
            {
                message = "Password must contain at least 8 characters."
            });
        }

        var account = await _context.Users.FirstOrDefaultAsync(u =>
            u.UserId == id &&
            (u.AccountType == "Staff" || u.AccountType == "Admin"));

        if (account == null)
        {
            return NotFound(new { message = "Staff or Admin account not found." });
        }

        account.PasswordHash = _passwordHasher.HashPassword(
            account, request.NewPassword);

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
