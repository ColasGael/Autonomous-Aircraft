import numpy as np

'''
Useful classes and functions are defined here
'''

def coord_to_dist(loc1, loc2):
	'''Compute distance (m) beteen two GPS lat/long positions using Haversine formula
	'''
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

class Graph:
	'''Graph represents the network of vertiports
	
	Attributes:
		'n' (int) : number of nodes
		'nodes' (n*2 double np.array) : position of the nodes in the GPS format, nodes[i] = [latitude, longitude]
		'adj'	(n*n int np.array) : adjacency matrix, adj[i,j] = 1 if connexion between vertiports i and j, 0 otherwise
	'''
	
	def __init__(self, nodes, adj):
		self.n = len(nodes)
		self.nodes = np.array(nodes)
		# self.adj = np.array(adj)
	
	def dist (self, i, j):
		'''compute the euclidian distance between nodes i and join
		
		Args:
			'i' (int) : start node
			'j' (int) : end node
		
		Returns:
			d (double) : euclidian distance between i and join
		'''
		d = coord_to_dist(nodes[i,:], nodes[j,:])
		
		return d
	
	def is_connected (self, i, j):
		'''check if 2 nodes are connected
		
		Args:
			i (int) : start node
			j (int) : end node
		
		Returns:
			is_connected (bool) : true if i and j are connected in the graph self
		'''
		return adj[i,j] == 1

class Query:
	'''Query represents the query of a client
	
	Attributes:
		'id' (int) : unique id of the query
		't' (double) : time at which the query as been made
		'start' (int) : id of the starting vertiport
		'end' (int) : id of the destination vertiport
	'''
	
	def __init__(self, id, t, start, end):
		self.id = id
		self.t = t
		self.start = start
		self.end = end

class Bid:
	'''Bid represents the bid made by our team to a client query
	
	Attributes:
		'query_id' (int): unique id of the query
		'start' (int) : id of the starting vertiport
		'end' (int) : id of the destination vertiport
		'drone' (Drone) : drone affected to the bid
		'time' (double) : estimated time of travel
		'amount' (double) : price proposed to the client
		'accepted' (bool) : has the bid been accepted by the client
		'profit' (double) : profit made on this trip
	'''
		
	def __init__(self, query, drone=None, estimated_time=0, amount=0, accepted=False, profit=0):
		self.querry_id = query.id
		self.start = query.start
		self.end = query.end
		
		self.drone = drone
		self.estimated_time = estimated_time
		self.amount = amount
		self.accepted = accepted
		self.profit = profit
