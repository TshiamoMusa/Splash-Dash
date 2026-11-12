namespace SplashDash.Api.Models;

public class Payment
{
    public int PaymentId { get; set; }

    public int BookingId { get; set; }

    public string PaymentMethod { get; set; } = string.Empty;

    public decimal Amount { get; set; }

    public string PaymentStatus { get; set; } = "Pending";

    public string? ReferenceNumber { get; set; }

    public string? TransactionId { get; set; }

    public DateTimeOffset? PaidAt { get; set; }

    public Booking? Booking { get; set; }
}