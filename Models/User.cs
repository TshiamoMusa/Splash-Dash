
namespace SplashDash.Api.Models;

public class User
{
    public int UserId { get; set; }

    public string FirstName { get; set; } = string.Empty;

    public string Surname { get; set; } = string.Empty;

    public string? Username { get; set; }

    public string Email { get; set; } = string.Empty;

    public string PhoneNumber { get; set; } = string.Empty;

    public string? PasswordHash { get; set; }

    public string? FirebaseUid { get; set; }

    public string AccountType { get; set; } = "Customer";

    public bool IsSuperAdmin { get; set; } = false;

    public bool IsActive { get; set; } = true;

    public DateTimeOffset CreatedAt { get; set; }

    public Admin? AdminProfile { get; set; }

    public Staff? StaffProfile { get; set; }

    public Customer? CustomerProfile { get; set; }
}
