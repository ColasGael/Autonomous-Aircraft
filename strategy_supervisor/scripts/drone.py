import numpy as np
from enum import Enum

from util import *

#	TO ADD : 
#	the following parameters must be modified to match the performances of our actual drone
#	- 'speed' in the class drone
#	- the 'battery_depletion' function : estimate the power used over a trip

class Status(Enum):
	'''Define the possible status of a drone
	
	Possible values
		'IDLE' = 1 : drone waiting for a query in hover at a vertiport
		'TO_RIDE' = 2 : drone moving to pick a passenger
		'RIDE' = 3 : drone transporting passenger to destination
		'TO_BASE' = 4 : drone going to base (for charging)
		'OUT' = 5 : drone unavailable
	'''
	
	IDLE = 1
	TO_RIDE = 2
	RIDE = 3
	TO_BASE = 4
	OUT = 5

class Drone:
	'''class representing a Drone (virtual or not)
	
	Attributes:
		'id' (int) : unique id of the drone
		'status' (Status) : status of the drone
		'position' (np.array) : position of the drone [latitude, longitude, height]
		'goal' (np.array) : goal position of the drone in the GPS format [latitude, longitude]
		
		'battery' (double) : level of the drone battery (between 0 and 1)
		'speed' (double) : forward speed of the drone (in m/s)

		'p_takeoff' (double) : percentage of battery used for takeoff
		'p_landing' (double) : percentage of battery used for landing		
		'p_travel' (double) : percentage of battery used in forward flight per meter
		
	Remarks:
		PLEASE FILL IN !
		- the 'speed' attributes has to be inferred from Task 1 (without wings) and Task 3 (with wings)
		- 'position' in the GPS format [latitude in ???, longitude in ???, height in ???]
	'''	
	speed = 7	# in m/s
	p_takeoff = 0
	p_landing = 0
	p_travel = 0
	base_position = np.array([0,0,100])
	
	def __init__(self, drone_id, status = Status.IDLE, position=base_position, battery_level=1):
		self.id = drone_id
		self.status = status
		self.position = np.array(position)
		self.battery = battery_level

	def set_status(self, status):
		self.status = status
	def get_position(self, position):
		self.position = np.array(position)
	def set_battery(self, battery_level):
		self.battery = battery_level
	def set_speed(self, speed):
		self.speed = speed

	def get_id(self):
		return self.id
	def get_status(self):
		return self.status
	def get_position(self):
		return self.position
	def get_battery_level(self):
		return self.battery_level
	def get_speed(self):
		return self.speed

	def set_goal(self, direction, distance):
		self.goal_dir = direction
		self.goal_dist = distance
		
	def battery_depletion (self, dist):
		'''compute the battery depletion (percentage) to fly this distance
		
		Args:
			'dist' (double) : distance to fly
		
		Returns:
			'depl' (double) : battery depletion to fly the corresponding distance
		'''
		
		depl = self.p_takeoff + self.p_landing + dist*self.p_travel
		
		return depl
	
	def dist_trip (self, loc_start, loc_end):
		'''compute the distance to make the trip (current position - start - end)
		
		Args:
			'loc_start' (double np.array) : position of the start in the GPS format [latitude, longitude]
			'loc_end' (double np.array) : position of the end in the GPS format [latitude, longitude]
		
		Returns:
			'dist' (double) : distance from the current position to the goal
		'''
		
		dist = coord_to_dist(self.position, loc_start) + coord_to_dist(loc_start, loc_end)
		
		return dist
		
	def go_to(self, goal):
		self.goal = goal
		self.status  = TO_RIDE
#		send goal to the android app with drone id	