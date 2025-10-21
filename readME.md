# Elevator Simulation (Back-End Code Challenge)

This project simulates an elevator control system managing multiple elevator cars across a configurable range of floors. It was written in Java as part of a back-end engineering challenge.

## Overview

The simulation models:
- Passenger requests (origin → destination)
- Elevator behavior (movement, door cycles, direction)
- A controller that dispatches requests to the most appropriate elevator using a simple cost heuristic

The simulation runs in discrete time steps (“ticks”), printing the state of each elevator every few ticks.

## Features

- Multiple elevators managed by a centralized controller  
- Request queue system separating unassigned and floor-level waiting passengers  
- Intelligent dispatching that prefers idle or direction-aligned elevators  
- Door timing simulation and per-floor boarding logic  
- Configurable building size, number of elevators, and simulation duration  
