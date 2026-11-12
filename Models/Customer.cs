namespace SplashDash.Api.Models;

public class Customer
{
    public int CustomerId { get; set; }

    public int UserId { get; set; }

    public string HomeAddress { get; set; } = string.Empty;

    public User? User { get; set; }

    public ICollection<Booking> Bookings { get; set; } = new List<Booking>();
}