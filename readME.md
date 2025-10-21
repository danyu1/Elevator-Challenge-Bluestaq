# Elevator Simulation (Back-End Code Challenge)

This is a back-end engineering challenge that was done in Java simulating an elevator control system that manages multiple elevator cars for a configurable number of floors.

## Overview

The simulation models:
- Passenger requests (origin → destination)
- Elevator behavior (movement, door cycles, direction)
- A controller that allocates requests to the best-fit elevator according to a simple cost heuristic

The simulation moves forward in discrete time intervals ("ticks") and outputs the state of all elevators periodically, every few ticks.

## Features

- A group of elevators managed by a central controller
- Request queue model with unassigned and floor-level waiting riders differentiated
- Intelligent dispatching favoring idle or direction-matched elevators
- Door timing simulation and per-floor boarding logic
- Building size, number of elevators, and simulation length configurable
