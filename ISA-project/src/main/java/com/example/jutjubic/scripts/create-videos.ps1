# PowerShell script to create 13 videos via API
# Base URL for the API
$baseUrl = "http://localhost:8080/api/videos/create"

# User object (same for all videos)
$user = @{
    id = "73659953-a1c5-428b-ab11-dc3d14567a00"
    username = "Snoopy"
    password = "`$2a`$10`$HqxUJ5nfGxtzfdFtsFwh9O6ZRJ.IoX8zkBShbBoTcSXvRhAP3zCY6"
    email = "ridiw20471@dubokutv.com"
    firstName = "Snoop"
    lastName = "Dogg"
    address = "address"
    createdAt = "2026-01-03T19:40:40"
    updatedAt = "2026-01-03T19:40:40"
    enabled = $true
    verificationCode = $null
    verificationExpiration = $null
    role = 0
}

# Array of videos with different titles and descriptions
$videos = @(
    @{
        title = "React Fundamentals - Build Your First App"
        description = "Learn React from scratch and build your first interactive web application with components and hooks."
    },
    @{
        title = "Python for Data Science - Complete Guide"
        description = "Master Python for data analysis, visualization, and machine learning with practical examples."
    },
    @{
        title = "Docker and Kubernetes - Container Orchestration"
        description = "Deploy and manage containerized applications at scale using Docker and Kubernetes."
    },
    @{
        title = "AWS Cloud Practitioner - Certification Prep"
        description = "Prepare for the AWS Cloud Practitioner certification with comprehensive coverage of all exam topics."
    },
    @{
        title = "GraphQL API Design - Modern Backend Development"
        description = "Build flexible and efficient APIs using GraphQL with Node.js and Apollo Server."
    },
    @{
        title = "Machine Learning with TensorFlow"
        description = "Implement neural networks and deep learning models using TensorFlow and Keras."
    },
    @{
        title = "Vue.js 3 Composition API - Advanced Patterns"
        description = "Master the Vue 3 Composition API with reusable composables and advanced state management."
    },
    @{
        title = "PostgreSQL Performance Tuning"
        description = "Optimize your PostgreSQL database for maximum performance with indexing and query optimization."
    },
    @{
        title = "Spring Boot Microservices Architecture"
        description = "Design and implement microservices using Spring Boot, Spring Cloud, and best practices."
    },
    @{
        title = "TypeScript Advanced Types and Patterns"
        description = "Deep dive into TypeScript generics, utility types, and advanced type manipulation."
    },
    @{
        title = "CI/CD with GitHub Actions"
        description = "Automate your build, test, and deployment pipeline using GitHub Actions workflows."
    },
    @{
        title = "Redis Caching Strategies"
        description = "Implement efficient caching patterns with Redis to improve application performance."
    },
    @{
        title = "Cybersecurity Fundamentals for Developers"
        description = "Learn essential security practices to protect your applications from common vulnerabilities."
    },
    @{
        title = "Next.js 14 - Full Stack React Framework"
        description = "Build production-ready React applications with server-side rendering and API routes."
    },
    @{
        title = "MongoDB Advanced Aggregation Pipelines"
        description = "Master complex data transformations and analytics using MongoDB aggregation framework."
    },
    @{
        title = "Rust Programming for Beginners"
        description = "Learn Rust from zero to hero with memory safety, concurrency, and systems programming."
    },
    @{
        title = "Kubernetes Helm Charts - Package Management"
        description = "Deploy and manage Kubernetes applications using Helm charts and templates."
    },
    @{
        title = "OAuth 2.0 and OpenID Connect Deep Dive"
        description = "Implement secure authentication and authorization in your applications."
    },
    @{
        title = "Apache Kafka - Event Streaming Platform"
        description = "Build real-time data pipelines and streaming applications with Apache Kafka."
    },
    @{
        title = "Flutter Mobile Development - Cross Platform Apps"
        description = "Create beautiful native mobile apps for iOS and Android with a single codebase."
    }
)

# Array of thumbnail numbers (1 to 7)
$thumbnailNumbers = 1..7

# Counter for tracking
$count = 0

foreach ($video in $videos) {
    $count++

    # Pick a random thumbnail number from the array
    $thumbNum = $thumbnailNumbers | Get-Random

    # Create the request body
    $body = @{
        title = $video.title
        description = $video.description
        videoPath = "videos/video1.mp4"
        thumbnailPath = "thumbnails/thumb$thumbNum.jpg"
        thumbnailCompressedPath = "thumbnails/thumb${thumbNum}_compressed.jpg"
        fileSize = Get-Random -Minimum 5000000 -Maximum 500000000
        duration = "{0:D2}:{1:D2}:{2:D2}" -f (Get-Random -Minimum 0 -Maximum 2), (Get-Random -Minimum 10 -Maximum 59), (Get-Random -Minimum 0 -Maximum 59)
        transcoded = $false
        scheduledAt = $null
        country = @("Serbia", "USA", "Germany", "UK", "France", "Canada")[(Get-Random -Minimum 0 -Maximum 6)]
        viewCount = 0
        user = $user
    } | ConvertTo-Json -Depth 3

    Write-Host "Creating video $count : $($video.title)" -ForegroundColor Cyan

    try {
        $response = Invoke-RestMethod -Uri $baseUrl -Method Post -Body $body -ContentType "application/json"
        Write-Host "  Success!" -ForegroundColor Green
    }
    catch {
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    }

    # Small delay between requests
    Start-Sleep -Milliseconds 500
}

Write-Host "`nCompleted! Created $count videos." -ForegroundColor Yellow

