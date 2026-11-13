# Adds the login guard to every admin and staff page.
# Run this from INSIDE the website_source folder:
#     cd "C:\Users\HP\VS Codes\Splash-and-Dash-\website_source"
#     powershell -ExecutionPolicy Bypass -File .\add-guard.ps1
# Safe to run twice: pages that already have the guard are skipped.

$pages = [ordered]@{
    "admin - dashboard.html"        = "Admin"
    "admin - manage_bookings.html"  = "Admin"
    "admin - manage_customers.html" = "Admin"
    "admin - manage_staff.html"     = "Admin"
    "admin - reports.html"          = "Admin"
    "staff - dashboard.html"        = "Staff"
    "staff - service_queue.html"    = "Staff"
    "staff - update_status.html"    = "Staff"
}

foreach ($name in $pages.Keys) {
    if (-not (Test-Path -LiteralPath $name)) {
        Write-Host "MISSING      $name" -ForegroundColor Yellow
        continue
    }

    $html = Get-Content -LiteralPath $name -Raw -Encoding UTF8

    if ($html.Contains("portal-auth.js")) {
        Write-Host "already done $name"
        continue
    }

    if (-not $html.Contains("</head>")) {
        Write-Host "NO </head>   $name (add the two script lines by hand)" -ForegroundColor Yellow
        continue
    }

    $role = $pages[$name]
    $snippet = "    <script src=`"js/portal-auth.js`"></script>`r`n" +
               "    <script>Portal.requireRole(`"$role`");</script>`r`n" +
               "</head>"

    $html = $html.Replace("</head>", $snippet)
    Set-Content -LiteralPath $name -Value $html -Encoding UTF8 -NoNewline
    Write-Host "guarded      $name ($role only)" -ForegroundColor Green
}