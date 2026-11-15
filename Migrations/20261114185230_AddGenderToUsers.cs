
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace SplashDash.Api.Migrations
{
    public partial class AddGenderToUsers : Migration
    {
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "gender",
                table: "users",
                type: "character varying(10)",
                maxLength: 10,
                nullable: false,
                defaultValue: "Undefined");

            // Copy existing Staff Gender values into the shared User table.
            migrationBuilder.Sql(@"
                UPDATE users AS u
                SET gender = COALESCE(NULLIF(s.gender, ''), 'Undefined')
                FROM staff AS s
                WHERE s.user_id = u.user_id;
            ");
        }

        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "gender",
                table: "users");
        }
    }
}
