import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
from enum import Enum
from math import sin, cos, sqrt, atan2, radians

# TO IMPLEMENT:
#	- broadcasting
#	- bid results reception
#	- transformations from GPS coordinates to distance and direction
#	- optimization function
#	- cost function

class Status(Enum):
	IDLE = 1
	TO_RIDE = 2
	RIDE = 3
	TO_BASE = 4


class Drone:
	def __init__(self, drone_id, status = Status.IDLE, position=[0,0], battery_level=1, speed=1):
		self.id = drone_id
		self.status = status
		self.position = position
		self.battery_level = battery_level

	def update_status(self, status):
		self.status = status
	def update_pos(self, position):
		self.position = position
	def update_battery_level(self, battery_level):
		self.battery_level = battery_level
	def update_speed(self, speed):
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

class Bid:
	def __init__(self, querry_id, drone=None, amount=0, estimated_time=0, accepted=False):
		self.querry_id = querry_id
		self.drone = drone
		self.amount = amount
		self.estimated_time = estimated_time
		self.accepted = accepted

# cost function
def cost_function():
	return 0

# function to optimize the allocation of drones to querries
def optimize(idle_drones, querries, cost_fun):
	bids = []
	for i in range(len(idle_drones)):
		bids += [Bid(i,idle_drones[i],np.random.rand(),np.random.rand()*100,True)]
	min_cost = np.random.rand()*50
	revenue = np.random.rand()*50
	return bids, min_cost, revenue

def broadcast_bids(bids):
	return 0

# Compute distance (m) beteen two GPS lat/long positions using Haversine formula
def coord_to_dist(loc1, loc2):
	lat1 = radians(loc1[0])
	lon1 = radians(loc1[1])
	lat2 = radians(loc2[0])
	lon2 = radians(loc2[1])

	#approximate radius of Earth in m
	R = 6371e3
	dlon = lon2 - lon1
	dlat = lat2 - lat1

	a = sin(dlat / 2)**2 + cos(lat1) * cos(lat2) * sin(dlon / 2)**2
	c = 2 * atan2(sqrt(a), sqrt(1 - a))

	distance = R * c
	return distance

def coord_to_direction(loc1, loc2):
	lat1 = radians(loc1[0])
	lon1 = radians(loc1[1])
	lat2 = radians(loc2[0])
	lon2 = radians(loc2[1])

	bearing = atan2(sin(lon2-lon1)*cos(lat2), cos(lat1)*sin(lat2) - sin(lat1)*cos(lat2)*cos(lon2-lon1))

	return bearing

# Main function for bidding. Takes the list of drones,
# the list of querries and some other arguments to be defined
# and decides what querries to bid on
def bidding(drones, querries, cost_fun):

	# Saving querries as CSV file
	log = querries#, 'bid_amount', 'bid_estimated_time', 'accepted'])
	log['bid_amount'] = None
	log['bid_estimated_time'] = None
	log['accepted'] = False

	# Find idle drones
	idle_drones = [drone for drone in drones if drone.status==Status.IDLE]
	
	# Find optimal bids
	bids, min_cost, revenue = optimize(idle_drones, querries, cost_fun)

	# Broadcast bids
	broadcast_bids(bids)
	print('Broadcasting bids...')

	# Receive answers (TO IMPLEMENT)
	# For the simulation, we'll assume all bids were accepted
	print('Receiving answers...')


	# Accept rides
	print('Confirming rides...')

	# Update drones status and log for accepted bids
	for bid in bids:
		if bid.accepted:
			drone = bid.drone
			drone.update_status(Status.TO_RIDE)
			current_loc = drone.get_position()
			querry_id = bid.querry_id
			querry = querries.iloc[querry_id]
			departure_loc = [querry['departure_lat'], querry['departure_long']]

			direction_to_ride = coord_to_direction(current_loc, departure_loc)
			dist_to_ride = coord_to_dist(current_loc, departure_loc)
			drone.set_goal(direction_to_ride, dist_to_ride)

			log.loc[querry_id,'bid_amount'] = bid.amount
			log.loc[querry_id,'bid_estimated_time'] = bid.estimated_time
			log.loc[querry_id,'accepted'] = True


	log.to_csv('../logs/log.csv')


def main():
	n_drones = 3
	drones = [Drone(i,position=[37.421305, -122.174920]) for i in range(n_drones)]
	querries = pd.read_excel('../input/test_querries.xlsx',header = None,names = ['request_time', 'departure_lat', 'departure_long', 'arrival_lat', 'arrival_long'])
	print(querries.head())
	bidding(drones, querries, cost_function)

if __name__ == '__main__':
	main()


