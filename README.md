# Autonomous-Aircraft
by Matt Croce, Aaron Wienkers, Ben Hightower, Ianis Bougdal-Lambert, Leila Taleghani and Gael Colas graduate students at Stanford.

This is our final project for the AA241X: "Autonomous Aircraft: Design, Build, Fly" class in Stanford School of Engineering (2018). Our teachers were Pr. Ilan Kroo and Pr. Juan Alonso.

Languages: Python, Java

Goal: design, build, and fly an autonomous eVTOL aircraft model that maximizes the revenue of your aerial transportation company (similar to Uber Elevate).

Our initial eVTOL aircraft was a DJI Spark Drone.

This project involved 3 distinct parts:
  - Aerodynamic Design : design and build wings that improve the performances of the drone (endurance, power consumption...) ;
  - Android App : code an Android App to control the drone autonomously from a computer ;
  - Strategy : define and implement in Python a bidding strategy that maximizes the profit of our team. A "Supervisor" node communicates with the server and affects the available drones to clients' requests according to this strategy. The corresponding files are stored in the "strategy_supervisor" folder.

This was a competition between 4 teams, every one of them trying to win the clients' bid to maximize their profit.
  
This repository gather our Python code for the strategy, and the Android code of the application.
