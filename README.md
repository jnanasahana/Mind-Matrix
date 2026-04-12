⛽ Fuel Mileage Tracker Pro
A modern, high-performance Android application built to help users track fuel efficiency across multiple vehicle types. This project focuses on precision, data visualization, and a seamless Material 3 user experience.

🚀 Key Features
Multi-Vehicle Support: Specialized tracking for Cars, Bikes, Trucks, and Scooters, accommodating different tank capacities and efficiency standards.

Dual Entry Modes:

Standard Mode: Track efficiency based on Liters and Kilometers.

Cash Mode: Calculate mileage based on currency spent—perfect for quick refills.

Data Visualization: Dynamic line charts built from scratch using Compose Canvas API to visualize fuel consumption trends over time.

Adaptive UI: Fully responsive layouts designed for Phones, Tablets, and Foldables using Material 3 adaptive navigation components.


com.example.fueltracker/
├── data/           # Room DB, Entities, and Daos
├── repository/     # Data source management
├── ui/
│   ├── components/ # Reusable Compose UI elements
│   ├── screens/    # Feature-specific screens (Home, History, Stats)
│   └── theme/      # Material 3 Color Schemes & Typography
└── viewmodel/      # Business logic and UI state management

Getting Started
Clone the repo:

Bash
git clone https://github.com/yourusername/fuel-mileage-pro.git
Open in Android Studio: Ensure you are using the latest Ladybug or Koala version for full Compose support.

Build & Run: Select your emulator or physical device and hit Run.

