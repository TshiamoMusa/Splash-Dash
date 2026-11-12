namespace SplashDash.Api.Models;

public class Booking
{
    public int BookingId { get; set; }

    public int CustomerId { get; set; }

    public int? StaffId { get; set; }

    public int ServiceId { get; set; }

    public string VehicleType { get; set; } = string.Empty;

    public DateTimeOffset BookingDateTime { get; set; }

    public string LocationType { get; set; } = string.Empty;

    public string? BookingAddress { get; set; }

    public string BookingStatus { get; set; } = "Pending";

    public decimal TotalAmount { get; set; }

    public Customer? Customer { get; set; }

    public Staff? Staff { get; set; }

    public Service? Service { get; set; }

    public Payment? Payment { get; set; }
}