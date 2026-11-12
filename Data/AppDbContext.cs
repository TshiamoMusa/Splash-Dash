using Microsoft.EntityFrameworkCore;
using SplashDash.Api.Models;

namespace SplashDash.Api.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options)
        : base(options)
    {
    }

    public DbSet<User> Users => Set<User>();
    public DbSet<Admin> Admins => Set<Admin>();
    public DbSet<Staff> Staff => Set<Staff>();
    public DbSet<Customer> Customers => Set<Customer>();
    public DbSet<Service> Services => Set<Service>();
    public DbSet<Booking> Bookings => Set<Booking>();
    public DbSet<Payment> Payments => Set<Payment>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // USERS
        modelBuilder.Entity<User>(entity =>
        {
            entity.ToTable("users");
            entity.HasKey(e => e.UserId);

            entity.Property(e => e.UserId).HasColumnName("user_id");
            entity.Property(e => e.FirstName).HasColumnName("first_name").HasMaxLength(50);
            entity.Property(e => e.Surname).HasColumnName("surname").HasMaxLength(50);
            entity.Property(e => e.Username).HasColumnName("username").HasMaxLength(50);
            entity.Property(e => e.Email).HasColumnName("email").HasMaxLength(150);
            entity.Property(e => e.PhoneNumber).HasColumnName("phone_number").HasMaxLength(20);
            entity.Property(e => e.PasswordHash).HasColumnName("password_hash").HasColumnType("text");
            entity.Property(e => e.FirebaseUid).HasColumnName("firebase_uid").HasMaxLength(150);
            entity.Property(e => e.AccountType).HasColumnName("account_type").HasMaxLength(20);
            entity.Property(e => e.IsActive).HasColumnName("is_active");
            entity.Property(e => e.CreatedAt).HasColumnName("created_at").HasColumnType("timestamp with time zone");
        });

        // ADMINS
        modelBuilder.Entity<Admin>(entity =>
        {
            entity.ToTable("admins");
            entity.HasKey(e => e.AdminId);

            entity.Property(e => e.AdminId).HasColumnName("admin_id");
            entity.Property(e => e.UserId).HasColumnName("user_id");

            entity.HasIndex(e => e.UserId).IsUnique();

            entity.HasOne(e => e.User)
                .WithOne(e => e.AdminProfile)
                .HasForeignKey<Admin>(e => e.UserId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        // STAFF
        modelBuilder.Entity<Staff>(entity =>
        {
            entity.ToTable("staff");
            entity.HasKey(e => e.StaffId);

            entity.Property(e => e.StaffId).HasColumnName("staff_id");
            entity.Property(e => e.UserId).HasColumnName("user_id");
            entity.Property(e => e.Gender).HasColumnName("gender").HasMaxLength(10);

            entity.HasIndex(e => e.UserId).IsUnique();

            entity.HasOne(e => e.User)
                .WithOne(e => e.StaffProfile)
                .HasForeignKey<Staff>(e => e.UserId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        // CUSTOMERS
        modelBuilder.Entity<Customer>(entity =>
        {
            entity.ToTable("customers");
            entity.HasKey(e => e.CustomerId);

            entity.Property(e => e.CustomerId).HasColumnName("customer_id");
            entity.Property(e => e.UserId).HasColumnName("user_id");
            entity.Property(e => e.HomeAddress).HasColumnName("home_address").HasMaxLength(255);

            entity.HasIndex(e => e.UserId).IsUnique();

            entity.HasOne(e => e.User)
                .WithOne(e => e.CustomerProfile)
                .HasForeignKey<Customer>(e => e.UserId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        // SERVICES
        modelBuilder.Entity<Service>(entity =>
        {
            entity.ToTable("services");
            entity.HasKey(e => e.ServiceId);

            entity.Property(e => e.ServiceId).HasColumnName("service_id");
            entity.Property(e => e.ServiceName).HasColumnName("service_name").HasMaxLength(50);
            entity.Property(e => e.Description).HasColumnName("description").HasColumnType("text");
            entity.Property(e => e.Price).HasColumnName("price").HasPrecision(10, 2);
            entity.Property(e => e.IsActive).HasColumnName("is_active");
        });

        // BOOKINGS
        modelBuilder.Entity<Booking>(entity =>
        {
            entity.ToTable("bookings");
            entity.HasKey(e => e.BookingId);

            entity.Property(e => e.BookingId).HasColumnName("booking_id");
            entity.Property(e => e.CustomerId).HasColumnName("customer_id");
            entity.Property(e => e.StaffId).HasColumnName("staff_id");
            entity.Property(e => e.ServiceId).HasColumnName("service_id");
            entity.Property(e => e.VehicleType).HasColumnName("vehicle_type").HasMaxLength(30);
            entity.Property(e => e.BookingDateTime).HasColumnName("booking_datetime").HasColumnType("timestamp with time zone");
            entity.Property(e => e.LocationType).HasColumnName("location_type").HasMaxLength(20);
            entity.Property(e => e.BookingAddress).HasColumnName("booking_address").HasMaxLength(255);
            entity.Property(e => e.BookingStatus).HasColumnName("booking_status").HasMaxLength(30);
            entity.Property(e => e.TotalAmount).HasColumnName("total_amount").HasPrecision(10, 2);

            entity.HasOne(e => e.Customer)
                .WithMany(e => e.Bookings)
                .HasForeignKey(e => e.CustomerId)
                .OnDelete(DeleteBehavior.Restrict);

            entity.HasOne(e => e.Staff)
                .WithMany(e => e.Bookings)
                .HasForeignKey(e => e.StaffId)
                .OnDelete(DeleteBehavior.Restrict);

            entity.HasOne(e => e.Service)
                .WithMany(e => e.Bookings)
                .HasForeignKey(e => e.ServiceId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        // PAYMENTS
        modelBuilder.Entity<Payment>(entity =>
        {
            entity.ToTable("payments");
            entity.HasKey(e => e.PaymentId);

            entity.Property(e => e.PaymentId).HasColumnName("payment_id");
            entity.Property(e => e.BookingId).HasColumnName("booking_id");
            entity.Property(e => e.PaymentMethod).HasColumnName("payment_method").HasMaxLength(20);
            entity.Property(e => e.Amount).HasColumnName("amount").HasPrecision(10, 2);
            entity.Property(e => e.PaymentStatus).HasColumnName("payment_status").HasMaxLength(20);
            entity.Property(e => e.ReferenceNumber).HasColumnName("reference_number").HasMaxLength(100);
            entity.Property(e => e.TransactionId).HasColumnName("transaction_id").HasMaxLength(150);
            entity.Property(e => e.PaidAt).HasColumnName("paid_at").HasColumnType("timestamp with time zone");

            entity.HasOne(e => e.Booking)
                .WithOne(e => e.Payment)
                .HasForeignKey<Payment>(e => e.BookingId)
                .OnDelete(DeleteBehavior.Restrict);
        });
    }
}