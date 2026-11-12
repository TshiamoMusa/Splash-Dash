
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SplashDash.Api.Data;
using SplashDash.Api.Models;

namespace SplashDash.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class CustomersController : ControllerBase
{
    private readonly AppDbContext _context;
    private readonly PasswordHasher<User> _passwordHasher = new();

    public CustomersController(AppDbContext context)
    {
        _context = context;
    }

    // Customer registration is public.
    [AllowAnonymous]
    [HttpPost("register")]
    public async Task<IActionResult> Register(
        RegisterCustomerRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.FirstName) ||
            string.IsNullOrWhiteSpace(request.Surname) ||
            string.IsNullOrWhiteSpace(request.Email) ||
            string.IsNullOrWhiteSpace(request.PhoneNumber) ||
            string.IsNullOrWhiteSpace(request.HomeAddress) ||
            string.IsNullOrWhiteSpace(request.Password))
        {
            return BadRequest(new
            {
                message = "Please complete all registration fields."
            });
        }

        if (request.Password.Length < 8)
        {
            return BadRequest(new
            {
                message = "Password must contain at least 8 characters."
            });
        }

        string email = request.Email.Trim().ToLowerInvariant();

        bool emailExists = await _context.Users.AnyAsync(
            u => u.Email.ToLower() == email);

        if (emailExists)
        {
            return Conflict(new
            {
                message = "An account with this email already exists."
            });
        }

        var user = new User
        {
            FirstName = request.FirstName.Trim(),
            Surname = request.Surname.Trim(),
            Email = email,
            PhoneNumber = request.PhoneNumber.Trim(),
            AccountType = "Customer",
            IsActive = true,
            CreatedAt = DateTimeOffset.UtcNow
        };

        user.PasswordHash = _passwordHasher.HashPassword(
            user,
            request.Password);

        var customer = new Customer
        {
            User = user,
            HomeAddress = request.HomeAddress.Trim()
        };

        _context.Users.Add(user);
        _context.Customers.Add(customer);

        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = "Registration successful.",
            customer = new
            {
                id = customer.CustomerId,
                userId = user.UserId,
                user.FirstName,
                user.Surname,
                user.Email,
                user.PhoneNumber,
                customer.HomeAddress,
                role = user.AccountType
            }
        });
    }

    // Returns the logged-in customer's profile.
    [Authorize(Roles = "Customer")]
    [HttpGet("profile")]
    public async Task<IActionResult> GetProfile()
    {
        string? userIdValue =
            User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

        if (!int.TryParse(userIdValue, out int userId))
        {
            return Unauthorized();
        }

        var customer = await _context.Customers
            .Include(c => c.User)
            .FirstOrDefaultAsync(c => c.UserId == userId);

        if (customer?.User == null)
        {
            return NotFound(new
            {
                message = "Customer profile not found."
            });
        }

        return Ok(new
        {
            id = customer.CustomerId,
            userId = customer.UserId,
            customer.User.FirstName,
            customer.User.Surname,
            customer.User.Email,
            customer.User.PhoneNumber,
            customer.HomeAddress
        });
    }

    // Allows a customer to update their saved home address.
    [Authorize(Roles = "Customer")]
    [HttpPut("address")]
    public async Task<IActionResult> UpdateAddress(
        UpdateCustomerAddressRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.HomeAddress))
        {
            return BadRequest(new
            {
                message = "Home address is required."
            });
        }

        string? userIdValue =
            User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

        if (!int.TryParse(userIdValue, out int userId))
        {
            return Unauthorized();
        }

        var customer = await _context.Customers
            .FirstOrDefaultAsync(c => c.UserId == userId);

        if (customer == null)
        {
            return NotFound(new
            {
                message = "Customer profile not found."
            });
        }

        customer.HomeAddress = request.HomeAddress.Trim();

        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = "Home address updated successfully."
        });
    }
}

public class RegisterCustomerRequest
{
    public string FirstName { get; set; } = string.Empty;
    public string Surname { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string PhoneNumber { get; set; } = string.Empty;
    public string HomeAddress { get; set; } = string.Empty;
    public string Password { get; set; } = string.Empty;
}

public class UpdateCustomerAddressRequest
{
    public string HomeAddress { get; set; } = string.Empty;
}
