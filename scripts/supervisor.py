
import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
from enum import Enum

import util
import drone

from math import sin, cos, sqrt, atan2, radians

# TO IMPLEMENT:
#	- 'cost_function' function
# compute the cost of a trip : fixed cost + variable cost (as a function of the time of the trip)
#	- 'price_function' function of (the average price a client is willing to pay for a GIVEN TRIP for a GIVEN TIME)
# compute the amount of money to propose to the client for a trip
#	- 'optimize' function : according to strategy
# choose which queries to answer to, what amount to propose, which drone to affect
#	- broadcasting
#	- bid results reception
# communication with server and app
		
class Supervisor:
	'''Supervisor receives the queries, makes the bids and manages the drones
	
	Attributes:
		'drones' (Drone list) : list of available (identical) drones 
		'graph' (Graph) : graph of available vertiports
		'queries' (Query list) : list of queries to be answered
		'bids' (Bid list) : list of past bids
	
	Remarks
		- the list of bids provide a useful dataset to adapt our strategy
	'''

	def __init__(self, n_drones, graph)
		self.drones  = [Drone(id) for id in range(n_drones)]
		self.graph = graph
		self.bids = []
		
	def profit(self):
		'''Compute the total profit until now
		
		Returns:
			'profit' (double) : the total profit made
		'''
		
		# list of all the profits for each won bid
		profits = [bid.profit for bid in self.bids if bid.accepted = True]
		# compute the total profit
		profit = np.sum(profits)
		
		return profit		
		
	def cost_function(self, time):
		'''Compute the cost of making a trip of time length
		
		Args:
			'time' (double) : time length of the trip
		
		Returns:
			'cost' (double) : cost of the trip
		'''
		fixed_cost = 0
		variable_cost = 0
		
		cost = fixed_cost + variable_cost*time
		
		return cost
	
	def price_function(self, query, drone):
		'''Compute the amount to propose to the client for his trip
		
		Args:
			'query' (Query) : the query to answer to
			'drone' (Drone) : the drone affected to this trip
		
		Returns:
			'amount' (double) : the amount proposed to the client 
		'''
		
		loc_start, loc_end = self.graph.nodes[query.start, :], self.graph.nodes[query.end, :]
		dist = drone.dist_trip(loc_start, loc_end)
		time = dist/drone.speed
		
		return 1.1*self.cost_function(time)
	
	def pick_drone(self, drones, query):
		'''Pick the best drone to answer a query according to a prior strategy
		
		Args:
			'drones' (Drone list) : list of available drones
			'query' (Query) : query to be answered
		
		Returns:
			'drone' (Drone) : drone affected to the query
		
		Remarks:
			- the current strategy is to affect the closest available drone
		'''
		
		loc_start = self.graph.nodes[query.start, :]
		
		# sort the drones in increasing order according to their distance to the starting node		
		drones.sort(key = lambda x : coord_to_dist(x.position, loc_start))
		
		return drones[0]
	
	def write_bid (self, drones, query):
		'''Write a bid for a query 
		
		Args:
			'drones' (Drone list) : list of available drones
			'query' (Query) : query to be answered
		
		Returns:
			'bid' (Bid) : bid proposal for the query
		'''
		
		# affected drone
		drone = self.pick_drone(drones, query)
		# distance that the drone need to fly
		loc_start, loc_end = self.graph.nodes[query.start, :], self.graph.nodes[query.end, :]
		dist = drone.dist_trip(loc_start, loc_end)
		# time that the drone need to fly
		estimated_time = dist/drone.speed
		# amount proposed to the client for the trip
		amount = self.price_function(query, drone)
		# estimated profit for this trip
		profit = amount - self.cost_function(estimated_time)
		
		bid = Bid(query, drone, dist, estimated_time, amount, profit)
		
		return bid
	
	def optimize(self):
		'''Choose which queries to answer to, what amount to propose, which drone to affect according to a prior strategy
		
		Returns:
			'bids' (Bid list) : list of bids to make
			
		Remarks:
			- the current strategy is to answer as many queries as there are available drones
			- the queries are answered in decreasing estimated profit order
		'''
		# find available drones : they must be waiting and having enough battery to complete the trip
		idle_drones = [drone for drone in self.drones if (drone.status==Status.IDLE) and (drone.battery_depletion(drone.dist_trip(loc_start, loc_end)) < drone.battery)]	
		
		if not idle_drones:
			return []
			
		# construct bids proposal for all the queries
		proposal_bids = []
		
		for i in range(len(self.queries)):
			query = self.queries[i]
			bid = write_bid(idle_drones, query)
			
			if bid.profit > 0:
				proposal_bids.append(bid)
		
		# sort the bids proposal in decreasing profit order
		proposal_bids.sort(key = lambda x : -x.profit)
		
		bids = []
		# pick the best bids
		for i in range(np.min( len(proposal_bids), len(idle_drones))):
			bid = proposal_bids[i]
			drone = bid.drone
			
			# check that the drone has not been affected to a previous query
			if not drone in idle_drones:
				i -= 1
				# create a new bid proposal
				bid = write_bid(idle_drones, query)
				if bid.profit > 0:
					# push the new proposal
					proposal_bids[i] = bid
					proposal_bids.sort(key = lambda x : -x.profit)	
					
			else:
				# add a deifinitive bid
				bids.append(bid)
				# remove the corresponding query from the waiting queries
				self.queries = filter(lambda x: x.id == bid.query_id, self.queries)				
				# remove the affected drone from the list of available drone
				idle_drones.remove(drone)
		
		return bids
	
	def query_callback(self, query):
		'''Callback trigered when we receive a new query from the server
		
		Args:
			'query' (Query) : new query sent by the server
		'''
		self.queries.append(query)
		
	def bidding(self):
		'''bid on optimal queries according to prior strategy (see 'optimize')
		'''

		# find the optimal bids
		bids = self.optimize()

		# broadcast bids
		broadcast_bids(bids)
		print('Broadcasting bids...')
	
	def answer_callback (self, query_id, answer):
		'''Callback trigered when we receive an answer to a bid
		
		Args:
			'query_id' (Query) : id of the query we are receiving an answer for
			'answer' (bool) : 'True' if we won the bidding process
		'''
		
		if answer:
			print('Confirming rides...')
			# modify the corresponding bid
			bid = filter(lambda x: x.query_id == query_id, self.bids)
			bid.accepted = True 
			# sent the corresponding drone to do the trip
			loc_start, loc_end = self.graph.nodes[query.start, :], self.graph.nodes[query.end, :]
			bid.drone.make_trip(loc_start, loc_end)
			# compute the profit of the trip
			bid.profit = bid.amount - self.cost_function(bid.estimated_time)
			
	def loop(self):
		if not queries:
			print('No queries to affect')
		
		else:
			self.bidding()

def broadcast_bids(bids):
	return 0



