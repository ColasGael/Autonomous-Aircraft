import numpy as np
import matplotlib.pyplot as plt
import pandas as pd

from util import *
from drone import *
from supervisor import *

class SupervisorSimulation:
	'''class to simulate how Supervisor handles the requests
	
	Attributes:
		'supervisor' (Supervisor) : the supervisor node
		'queries' (Query list) : list of queries to be answered
	'''
	
	def __init__ (self, graph, n_drones, positions, queries):
		'''create an instance of SupervisorSimulation
		
		Args:
			'graph' (Graph) : graph of the vertices with their connections
			'n_drones' (int) : number of available drones
			'positions' (np.array, shape = (n_drones, 3)) : list of drone positions 
			'queries' (Query list) : list of queries to be answered
		'''
		
		self.supervisor = Supervisor(graph, n_drones, positions, queries)
		self.queries = queries
		
	def run (self):
		'''run the simulation
		
		Remarks:
			- compute the best bids that can be made : in decreasing estimated profit 
			- store the bids into a csv file
		'''
		
		bids = self.supervisor.optimize()
		
		# store the bids into a csv file
		bids_dict = {'query_id': [], 'drone_id': [], 'estimated_time': [], 'price': [], 'estimated_profit': []}
		
		for bid in bids:
			bids_dict['query_id'].append(bid.query.id)
			bids_dict['drone_id'].append(bid.drone.id)
			bids_dict['estimated_time'].append(bid.estimated_time)
			bids_dict['price'].append(bid.amount)
			bids_dict['estimated_profit'].append(bid.profit)
			
		bids_log = pd.DataFrame(bids_dict)
		
		bids_log.to_csv('../logs/log.csv')

def main():
	# define the graph
	nodes = 0.01*np.array([[0,0],[1,0],[2,0],[1,0],[1,1],[2,1]])
	adj = np.ones((6,6))
	graph = Graph(nodes, adj)
	
	# initial positions of the drones
	n_drones = 3
	positions = 0.01*np.array([[1,0],[0,0],[0.5,0.5]])
	
	# load the queries from the csv file
	queries_log = pd.read_csv('../input/queries.csv', names=['id', 'request_time', 'start_id', 'end_id'])

	n_queries = queries_log.shape[0]
	queries = [Query(*queries_log.iloc[i]) for i in range(n_queries)]
	
	# initialize the simulation
	sim = SupervisorSimulation(graph, n_drones, positions, queries)
	sim.run()

if __name__ == '__main__':
	main()


