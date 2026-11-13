using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SplashDash.Api.Data;

namespace SplashDash.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class ServicesController : ControllerBase
{
    private readonly AppDbContext _context;

    public ServicesController(AppDbContext context)
    {
        _context = context;
    }

    [AllowAnonymous]
    [HttpGet]
    public async Task<IActionResult> GetServices()
    {
        var services = await _context.Services
            .Where(s => s.IsActive)
            .Select(s => new
            {
                serviceId = s.ServiceId,
                serviceName = s.ServiceName,
                description = s.Description,
                price = s.Price
            })
            .ToListAsync();

        return Ok(services);
    }
}
