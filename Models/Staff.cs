namespace SplashDash.Api.Models;

public class Staff
{
    public int StaffId { get; set; }

    public int UserId { get; set; }

    public string Gender { get; set; } = string.Empty;

    public User? User { get; set; }

    public ICollection<Booking> Bookings { get; set; } = new List<Booking>();
}