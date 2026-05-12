1. Project Selection 
I have selected Project Title 04 – Android App Development using GenAI: Grama -Yatri 
(Mobility) as my individual project for Phase 2 of the MindMatrix VTU Internship Program. 
Grama-Yatri is a community-powered, crowdsourced bus tracking application designed 
specifically for rural Indian villages. The app transforms unpredictable village bus schedules 
into a live, real-time timetable created and maintained by the passengers themselves. By 
leveraging the "human-as-a-sensor" model, it eliminates the need for expensive GPS hardware 
while providing city grade transit predictability 
2. Problem Statement 
The Challenge  
Wait times for the "Village Bus" are unpredictable. Passengers—primarily laborers and 
students often miss the bus by just 2 minutes and face a grueling 3-hour wait for the next one. 
Core Social-Economic Impact 
• Lost productivity: Daily wage workers lose essential income due to unpredictable transit 
timings.  
• Missed educational opportunities: Students face academic disruption due to missed 
transportation to regional centers.  
• Economic hardship: Families are burdened by the inefficiency of rural mobility systems. 
3. Detailed Description (The Vision) 
Grama-Yatri is an innovative community-powered rural mobility application designed to 
solve one of the biggest transportation challenges in Indian villages—unpredictable bus 
timings. In many rural areas, buses do not follow strict schedules, causing daily wage workers, 
students, and travelers to miss buses by just a few minutes and then wait for hours for the next 
one. This results in lost income, missed educational opportunities, and unnecessary 
hardship. 
How It Works (Simple): 
A passenger sees the bus and "Pings" the app. The system calculates the Expected Time of 
Arrival (ETA) at all subsequent villages on that route using pre-defined travel intervals. 
1. Open the app and select your bus route.  
2. If you see the bus, tap “Bus Passed Me.”  
3. The app updates the bus location.  
4. It calculates when the bus will reach the next stops.  
5. Everyone using the app can see the live update.  
6. Users get a notification when the bus is near. 
4. Scope of the Application 
Geographic Coverage 
Rural village routes with fixed bus stops. 
User Base 
Villagers, laborers, students, and occasional travelers. 
Core Functionality 
Real-time tracking via crowdsourced pings. 
Data Management 
Firebase Realtime Database for instant synchronization. 
5. Functional Requirements 
FR-03: Bus Ping System 
FR-03.1 
Users shall report bus arrival with "Bus just passed me" option. 
FR-03.2 
Users shall report boarding with "I am on the bus" option. 
FR-03.3 
Each ping shall capture: timestamp, user ID, and stop location. 
FR-03.4 
Pings shall propagate to all users on the route in real-time. 
FR-04: Live ETA Calculation 
• System shall calculate ETA for downstream stops upon receiving a ping. 
• ETA display format: "Bus will reach your village in ~XX mins". 
• System shall show confidence level based on ping recency. 
6. Non-Functional Requirements 
NFR-01.2 
Ping propagation latency 
< 2 seconds to all users 
NFR-04.1 
Minimum Android Version 
Android 6.0 (API 23) 
NFR-04.3 
Network Compatibility 
App shall function on 2G/3G networks 
7. Features Classification 
MUST-HAVE FEATURES (MVP)  
• Route View: Vertical timeline display of all stops.  
• Bus Ping: Simple two-button interface.  
• Instant Sync: Firebase Realtime Database integration.  
• Reporter Attribution: Showing the source (e.g., "Reported by Ravi"). 
GOOD-TO-HAVE FEATURES (POST-MVP)  
• User Reputation: Trust scores based on reporting accuracy.  
• GenAI Predictions: ML-based predictions using historical ping data.  
• Voice Commands: Voice-activated pings for hands-free reporting. 
8. Technical Architecture 
• Efficient Mobility: Reduce wasted man-hours at bus stops by 70%.  
• Social Equity: Making rural transport as predictable as city metros 
9. Technical Architecture 
routes/ 
├── route_id/ 
│ 
│ 
│ 
│ 
│ 
├── name: "Village Express 101" 
└── active_bus/ 
├── last_ping_stop: "stop_2" 
├── last_ping_time: timestamp 
└── reported_by: "Ravi" 
10. Operational User Flow 
1. Selection: User opens the app and selects their active route.  
2. Observation: User views the vertical timeline. If a bus is active, the icon moves down the 
stops.  
3. Interaction: If the user is at the stop or on the bus, they use the "Ping" interface to update 
the status.  
4. Notification: Waiting users receive a push notification when the bus is 2 stops away 
11. Conclusion 
Grama-Yatri represents a paradigm shift in rural transportation. By digitizing the informal 
knowledge of the community, we create a reliable infrastructure that empowers students and 
workers. The reliance on serverless real-time databases ensures that even on slow networks, 
the "truth" of the bus location is shared instantly.
