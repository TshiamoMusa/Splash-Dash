using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SplashDash.Api.Data;
using SplashDash.Api.Models;
using System.Security.Claims;

namespace SplashDash.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class PaymentsController : ControllerBase
{
    private readonly AppDbContext _context;

    public PaymentsController(AppDbContext context)
    {
        _context = context;
    }

    [Authorize(Roles = "Customer")]
    [HttpPost]
    public async Task<IActionResult> MakePayment(CreatePaymentRequest request)
    {
        if (!int.TryParse(User.FindFirstValue(ClaimTypes.NameIdentifier), out int userId))
        {
            return Unauthorized();
        }

        var customer = await _context.Customers.FirstOrDefaultAsync(c => c.UserId == userId);
        if (customer == null)
        {
            return NotFound(new { message = "Customer account not found." });
        }

        var booking = await _context.Bookings
            .FirstOrDefaultAsync(b => b.BookingId == request.BookingId && b.CustomerId == customer.CustomerId);

        if (booking == null)
        {
            return NotFound(new { message = "Booking not found." });
        }

        if (booking.BookingStatus == "Cancelled" || booking.BookingStatus == "Completed")
        {
            return BadRequest(new { message = "Cannot pay for a cancelled or completed booking." });
        }

        string refNumber = "SD-PAY-" + Guid.NewGuid().ToString().ToUpper().Substring(0, 8);
        string txId = "TXN-" + new Random().Next(100000, 999999);

        var payment = new Payment
        {
            BookingId = booking.BookingId,
            PaymentMethod = request.PaymentMethod,
            Amount = booking.TotalAmount,
            PaymentStatus = "Paid",
            ReferenceNumber = refNumber,
            TransactionId = txId,
            PaidAt = DateTimeOffset.UtcNow
        };

        booking.BookingStatus = "Confirmed";

        _context.Payments.Add(payment);
        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = "Payment processed successfully.",
            paymentId = payment.PaymentId,
            referenceNumber = refNumber,
            transactionId = txId,
            amount = payment.Amount,
            status = payment.PaymentStatus
        });
    }
}

public class CreatePaymentRequest
{
    public int BookingId { get; set; }
    public string PaymentMethod { get; set; } = string.Empty;
    public string? CardNumber { get; set; }
    public string? Expiry { get; set; }
    public string? Cvv { get; set; }
}
