namespace SplashDash.Api.Models;

public class Service
{
    public int ServiceId { get; set; }

    public string ServiceName { get; set; } = string.Empty;

    public string? Description { get; set; }

    public decimal Price { get; set; }

    public bool IsActive { get; set; } = true;

    public ICollection<Booking> Bookings { get; set; } = new List<Booking>();
}