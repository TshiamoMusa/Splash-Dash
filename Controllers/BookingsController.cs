
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SplashDash.Api.Data;
using SplashDash.Api.Models;
using System.Security.Claims;

namespace SplashDash.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class BookingsController : ControllerBase
{
    private readonly AppDbContext _context;

    public BookingsController(AppDbContext context)
    {
        _context = context;
    }

    // Customers can view only their own bookings.
    [Authorize(Roles = "Customer")]
    [HttpGet("my-bookings")]
    public async Task<IActionResult> GetCustomerBookings()
    {
        if (!int.TryParse(
            User.FindFirstValue(ClaimTypes.NameIdentifier),
            out int userId))
        {
            return Unauthorized();
        }

        var customer = await _context.Customers
            .FirstOrDefaultAsync(c => c.UserId == userId);

        if (customer == null)
        {
            return NotFound(new
            {
                message = "Customer account not found."
            });
        }

        var bookings = await _context.Bookings
            .Where(b => b.CustomerId == customer.CustomerId)
            .Include(b => b.Service)
            .OrderByDescending(b => b.BookingDateTime)
            .Select(b => new
            {
                b.BookingId,
                b.VehicleType,
                Service = b.Service!.ServiceName,
                b.BookingDateTime,
                b.LocationType,
                b.BookingAddress,
                b.BookingStatus,
                b.TotalAmount
            })
            .ToListAsync();

        return Ok(bookings);
    }

    // Staff and Admin can view the shared booking queue.
    [Authorize(Roles = "Staff,Admin")]
    [HttpGet("all")]
    public async Task<IActionResult> GetAllBookings()
    {
        var bookings = await _context.Bookings
            .Include(b => b.Customer)
                .ThenInclude(c => c!.User)
            .Include(b => b.Service)
            .Include(b => b.Staff)
                .ThenInclude(s => s!.User)
            .OrderBy(b => b.BookingDateTime)
            .Select(b => new
            {
                b.BookingId,
                b.CustomerId,
                CustomerName = b.Customer!.User!.FirstName
                    + " " + b.Customer.User.Surname,
                CustomerPhone = b.Customer.User.PhoneNumber,
                b.VehicleType,
                Service = b.Service!.ServiceName,
                b.BookingDateTime,
                b.LocationType,
                b.BookingAddress,
                b.BookingStatus,
                b.TotalAmount,
                b.StaffId,
                StaffName = b.Staff == null
                    ? null
                    : b.Staff.User!.FirstName + " " + b.Staff.User.Surname
            })
            .ToListAsync();

        return Ok(bookings);
    }

    // Customer creates a booking.
    [Authorize(Roles = "Customer")]
    [HttpPost]
    public async Task<IActionResult> CreateBooking(
        CreateBookingRequest request)
    {
        if (!int.TryParse(
            User.FindFirstValue(ClaimTypes.NameIdentifier),
            out int userId))
        {
            return Unauthorized();
        }

        if (string.IsNullOrWhiteSpace(request.VehicleType) ||
            string.IsNullOrWhiteSpace(request.LocationType) ||
            request.ServiceId <= 0)
        {
            return BadRequest(new
            {
                message = "Please complete all booking details."
            });
        }

        string[] allowedVehicles =
        {
            "Small Car", "Large Car", "SUV", "Bakkie"
        };

        if (!allowedVehicles.Contains(request.VehicleType))
        {
            return BadRequest(new
            {
                message = "Invalid vehicle type."
            });
        }

        if (request.LocationType != "On-site" &&
            request.LocationType != "House Call")
        {
            return BadRequest(new
            {
                message = "Invalid booking location."
            });
        }

        var customer = await _context.Customers
            .FirstOrDefaultAsync(c => c.UserId == userId);

        if (customer == null)
        {
            return NotFound(new
            {
                message = "Customer account not found."
            });
        }

        var service = await _context.Services
            .FirstOrDefaultAsync(s =>
                s.ServiceId == request.ServiceId && s.IsActive);

        if (service == null || service.Price <= 0)
        {
            return BadRequest(new
            {
                message = "Selected service is unavailable."
            });
        }

        if (request.BookingDateTime <= DateTimeOffset.UtcNow)
        {
            return BadRequest(new
            {
                message = "Please select a future booking date and time."
            });
        }

        string? bookingAddress = null;

        if (request.LocationType == "House Call")
        {
            bookingAddress = string.IsNullOrWhiteSpace(request.BookingAddress)
                ? customer.HomeAddress
                : request.BookingAddress.Trim();

            if (string.IsNullOrWhiteSpace(bookingAddress))
            {
                return BadRequest(new
                {
                    message = "A house-call address is required."
                });
            }
        }

        var booking = new Booking
        {
            CustomerId = customer.CustomerId,
            ServiceId = service.ServiceId,
            VehicleType = request.VehicleType,
            BookingDateTime = request.BookingDateTime,
            LocationType = request.LocationType,
            BookingAddress = bookingAddress,
            BookingStatus = "Pending",
            TotalAmount = service.Price
        };

        _context.Bookings.Add(booking);

        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = "Your booking was created successfully.",
            booking = new
            {
                booking.BookingId,
                booking.CustomerId,
                booking.ServiceId,
                service.ServiceName,
                booking.VehicleType,
                booking.BookingDateTime,
                booking.LocationType,
                booking.BookingAddress,
                booking.BookingStatus,
                booking.TotalAmount
            }
        });
    }

    // Staff and Admin can update booking status.
    [Authorize(Roles = "Staff,Admin")]
    [HttpPut("{id:int}/status")]
    public async Task<IActionResult> UpdateBookingStatus(
        int id,
        UpdateBookingStatusRequest request)
    {
        var booking = await _context.Bookings
            .FirstOrDefaultAsync(b => b.BookingId == id);

        if (booking == null)
        {
            return NotFound(new
            {
                message = "Booking not found."
            });
        }

        string[] allowedStatuses =
        {
            "Pending",
            "Confirmed",
            "In Progress",
            "Completed",
            "Cancelled"
        };

        if (!allowedStatuses.Contains(request.Status))
        {
            return BadRequest(new
            {
                message = "Invalid booking status."
            });
        }

        booking.BookingStatus = request.Status;

        await _context.SaveChangesAsync();

        return Ok(new
        {
            message = "Booking status updated successfully.",
            booking.BookingId,
            booking.BookingStatus
        });
    }

    // Customer updates their own pending booking.
    [Authorize(Roles = "Customer")]
    [HttpPut("{id:int}")]
    public async Task<IActionResult> UpdateBooking(int id, CreateBookingRequest request)
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
            .FirstOrDefaultAsync(b => b.BookingId == id && b.CustomerId == customer.CustomerId);

        if (booking == null)
        {
            return NotFound(new { message = "Booking not found." });
        }

        if (booking.BookingStatus != "Pending")
        {
            return BadRequest(new { message = "Only pending bookings can be edited." });
        }

        var service = await _context.Services.FirstOrDefaultAsync(s => s.ServiceId == request.ServiceId && s.IsActive);
        if (service == null)
        {
            return BadRequest(new { message = "Selected service is unavailable." });
        }

        booking.ServiceId = service.ServiceId;
        booking.VehicleType = request.VehicleType;
        booking.BookingDateTime = request.BookingDateTime;
        booking.LocationType = request.LocationType;
        booking.BookingAddress = request.LocationType == "House Call" ? (request.BookingAddress ?? customer.HomeAddress) : null;
        booking.TotalAmount = service.Price;

        await _context.SaveChangesAsync();

        return Ok(new { message = "Booking updated successfully." });
    }

    // Customer cancels their own pending booking.
    [Authorize(Roles = "Customer")]
    [HttpPut("{id:int}/cancel")]
    public async Task<IActionResult> CancelBooking(int id)
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
            .FirstOrDefaultAsync(b => b.BookingId == id && b.CustomerId == customer.CustomerId);

        if (booking == null)
        {
            return NotFound(new { message = "Booking not found." });
        }

        if (booking.BookingStatus != "Pending" && booking.BookingStatus != "Confirmed")
        {
            return BadRequest(new { message = "This booking cannot be cancelled at this stage." });
        }

        booking.BookingStatus = "Cancelled";
        await _context.SaveChangesAsync();

        return Ok(new { message = "Booking cancelled successfully." });
    }
}

public class CreateBookingRequest
{
    public int ServiceId { get; set; }

    public string VehicleType { get; set; } = string.Empty;

    public DateTimeOffset BookingDateTime { get; set; }

    public string LocationType { get; set; } = string.Empty;

    public string? BookingAddress { get; set; }
}

public class UpdateBookingStatusRequest
{
    public string Status { get; set; } = string.Empty;
}
