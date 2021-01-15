package familiarity;

import java.awt.Color;
import java.util.concurrent.ConcurrentLinkedQueue;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.field.continuous.Continuous2D;
import sim.field.grid.SparseGrid2D;
import sim.field.network.Edge;
import sim.util.Bag;
import sim.util.Double2D;

public abstract class Agent implements Steppable{

	// the agent's ID number
	public int id;
	// the agent's lifespan
	public int lifespan;
	// the agent's age
	public int age;
	// whether the agent cooperates
	public boolean coop;
	// means of removing this agent from the schedule
	public Stoppable stopbutton;
	// amount of fitness gained over its lifespan so far
	public double fitness;
	// individual parameters for evolution
	// bias in favor of familiar individuals
	public double famBias;
	// number of other agents an agent can remember at a time
	public double memory;
	// how many encounters it takes for an agent to become familiar
	public double lrate;
	// how long agents have to go without an encounter to no longer be familiar
	public double decay;
	// this agent's aggregative tendency (view range)
	public double viewrange;
	// number of familiar individuals
	public double famCount;
	// clustering coefficient around this agent
	public double localcluster;
	// TODO - figure out what these are actually storing (and make it x and y from Spatial Agent)
	// x coordinate on the social network
	public double netx;
	// y coordinate on the social network
	public double nety;
	// color for visualization
	public Color color;
	// this agent's weakest connection
	public Edge weakestlink;
	// the agent this agent interacted with on the previous step
	public Agent previnteract;
	// the number of steps this agent has interacted on
	public int interact;
	// the total number of offspring this agent has had
	public int offspring;
	
	/*
	 * initializes parameters
	 */
	public Agent(familiarModel model, double[] params, double initfitness, double x, double y) {
		// increment population size
		model.popsize++;
		// if the list of open slots isn't empty, take one of those as its ID and fill that slot
		if(!model.oneshot && model.agentslots.size() > 0) {
			this.id = model.agentslots.remove(0);
			model.agents[this.id] = this;
		}
		// schedule the agent
		this.stopbutton = model.schedule.scheduleRepeating(this);
		// add it as a node to the interaction network
		model.interactNet.addNode(this);
		// and to the familiarity network
		model.famNet.addNode(this);
		// draw its lifespan from a normal distribution (making sure it's at least 1)
		this.lifespan = (int) (model.drawGamma(model.minlifespan, model.varlifespan, 1));
		// initialize number of familiar individuals to zero
		this.famCount = 0;
		// initialize interactions to 0
		this.interact = 0;
		// initialize clustering to zero
		this.localcluster = 0;
		// randomly place on the network grid
		this.netx = x;
		this.nety = y;
		// place the agent in the space (whether discrete or continuous)
		if(model.cont) {
			((Continuous2D) model.agentNet).setObjectLocation(this, new Double2D(this.netx, this.nety));
		} else {
			((SparseGrid2D) model.agentNet).setObjectLocation(this, (int) this.netx, (int) this.nety);
		}
		// initialize color
		this.color = new Color(model.random.nextFloat(), model.random.nextFloat(), model.random.nextFloat());
		// set evolving parameters
		this.famBias = params[0];
		this.memory = params[1];
		this.lrate = params[2];
		this.decay = params[3];
		// add them to the strategy averages
		model.fbias[(int)params[4]] += this.famBias;
		model.fmem[(int)params[4]] += this.memory;
		model.fthresh[(int)params[4]] += this.lrate;
		model.fdecay[(int)params[4]] += this.decay;
		// if the game is a cooperative dilemma, indicate whether this individual is a cooperator
		if(model.game == 'c' || model.game == 'g') {
			if(params[4] == 1) {
				this.coop = true;
				// change its color accordingly (cooperators are blue)
				this.color = Color.BLUE;
				// also add it to the count
				model.coopCount++;
			} else {
				this.coop = false;
				// change its color accordingly (defectors are red)
				this.color = Color.RED;
			}
		}
		// change brightness based on whatever trait is coevolving
		double alpha = 1;
		if(model.evolfam) {
			alpha = this.famBias;	
		} else if(model.evolagg) {
			alpha = 1/(1 + Math.exp(-this.viewrange));
		}
		this.color = new Color(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), (int) (205 + 50*alpha));
		this.fitness = initfitness;
	}
	
	/*
	 * handles evolution
	 */
	public void step(SimState state) {
		familiarModel model = (familiarModel) state;
		// if the network is being used, take care of that
		if(model.net) {
			// start by pruning this agent's part of the network
			pruneNet(model);
			// subtract the previous number of familiar individuals from the count
			model.famCount -= this.famCount;
			// adjust this agent's number of familiar individuals
			this.famCount = model.famNet.getEdgesOut(this).numObjs/model.popsize;
			// add it to the count
			model.famCount += this.famCount;
		}
		// add/substract fitness from other sources
		this.fitness += model.otherfit;
		// if there's evolution and agents can continually die/reproduce, evolve
		if(model.evol && !model.oneshot) {
			this.age++;
			evolve(model);
		}
	}
	
	public void playGame(familiarModel model, Agent a) {
		// if the agent is null, do none of this stuff in advance
		if(a != null) {
			// whenever two agents interact, update their familiarity (if the network is being used)
			if(model.net) {
				updateFamiliar(model, a);
				a.updateFamiliar(model, this);
			}
			// store both agents' latest interaction
			this.previnteract = a;
			a.previnteract = this;
		}
		// determine payoffs based on the game
		switch(model.game) {
			// if this is a cooperation game, update according to strategies
			case 'c':
				// if this agent is a cooperator, it pays a cost and gives the other a benefit
				if(this.coop) {
					this.fitness -= model.fitcost;
					a.fitness += model.fitgain;
				}
				// if the other agent is a cooperator, it pays a cost and gives this agent a benefit
				if(a.coop) {
					a.fitness -= model.fitcost;
					this.fitness += model.fitgain;
				}
				// if aggregation tendency is also evolving, that comes with a cost (for both players)
				if(model.evolagg) {
					this.fitness -= model.aggcost*Math.pow(this.viewrange, 2);
					a.fitness -= model.aggcost*Math.pow(a.viewrange, 2);
				}
				break;
			// if evolving based just on familiarity, update fitness based on how familiar the two agents are
			case 'f':
				if(model.famNet.getEdge(this, a) != null) {
					this.fitness += (double) model.famNet.getEdge(this, a).getInfo();
				}
				if(model.famNet.getEdge(a, this) != null) {
					a.fitness += (double) model.famNet.getEdge(a, this).getInfo();
				}
				break;
			// if evolving based on interaction, increment fitness for both agents
			case 'i':
				this.fitness++;
				a.fitness++;
				break;
			// if this is the public goods game, find all the agents connected to this agent, and increment all their fitness together
			case 'g':
				// grab the space (because I'll be using it a bunch)
				Continuous2D space = (Continuous2D) model.agentNet;
				// start with a queue with just this agent
				ConcurrentLinkedQueue<Agent> search = new ConcurrentLinkedQueue<Agent>();
				search.add(this);
				this.interact++;
				// count the total number of cooperators and total number of group members
				double cooperators = 0;
				double groupsize = 0;
				// and put all the individuals found into a separate list
				ConcurrentLinkedQueue<Agent> found = new ConcurrentLinkedQueue<Agent>();
				// find neighbors until search is empty
				while(!search.isEmpty()) {
					// get the latest agent from search
					a = search.poll();
					// add to the group size
					groupsize++;
					// add it to the list
					found.add(a);
					// if this agent is a cooperator, add to the number of cooperators too
					if(a.coop) cooperators++;
					// then check all its neighbors within the range and add the ones that haven't been checked yet to search
					Bag neighbors = space.getNeighborsExactlyWithinDistance(new Double2D(a.netx, a.nety), model.interactrange);
					for(Object o: neighbors) {
						Agent n = (Agent) o;
						if(n.interact == 0) {
							search.add(n);
							n.interact++;
						}
					}
				}
				// then go back through the found agents and actually calculate their fitness
				while(!found.isEmpty()) {
					// grab the agent
					a = found.poll();
					// modulate the number of cooperators by whether this agent is one
					int c = 0;
					// if this agent is a cooperator, pay the corresponding cost and benefit from one fewer cooperator
					if(a.coop) {
						a.fitness -= model.fitcost;
						c = 1;
					}
					// add the benefit, based on the proportion of its groupmates that are cooperators (if it has any groupmates)
					if(groupsize > 1) a.fitness += model.fitgain*(cooperators-c)/(groupsize - 1);
					// also subtract the aggregation cost
					a.fitness -= model.aggcost*Math.pow(a.viewrange, 2);
				}
				break;
		}
	}
	
	/*
	 * decrements connections according to decay, prunes connections of zero, and returns the weakest connection
	 */
	public void pruneNet(familiarModel model) {
		// grab all edges from this node in the interaction network
		try {
			Bag neighbors = (Bag) model.interactNet.getEdgesOut(this).clone();
			// initialize weakest connection to null
			this.weakestlink = null;
			// decrement all of them according to decay
			for(int i = 0; i < neighbors.numObjs; i++) {
				// grab the edge
				Edge e = (Edge) neighbors.get(i);
				// grab the relationship strength
				double relstrength = (double) e.getInfo();
				// and the agent
				Agent a = (Agent) e.getTo();
				// now decrement it
				model.interactNet.updateEdge(e, this, a, relstrength-this.decay);
				// if the edge is now less than or equal to zero, remove it
				if(relstrength-this.decay <= 0){
					model.interactNet.removeEdge(e);
				}
				// determine whether this edge exists in the familiarity network too
				Edge f = model.famNet.getEdge(this, a);
				// if so, adjust it to match the interaction network, remove if necessary, and see if it's the weakest
				if(f != null) {
					// remove the old value of the edge from total familiarity
					model.familiarity -= (double) f.getInfo();
					// if the value is 0 remove it
					if(relstrength-this.decay <= 0) {
						model.famNet.removeEdge(f);
						model.edgecount--;
					} else {
						// otherwise, update it
						model.famNet.updateEdge(f, this, a, e.getInfo());
						model.familiarity += (double) f.getInfo();
						// and check to see if it's the weakest
						if(this.weakestlink == null || (double) f.getInfo() < (double) this.weakestlink.getInfo()) {
							this.weakestlink = f;
						}
					}
				}
			}
		} catch(CloneNotSupportedException e) {
			// this shouldn't happen
		}
	}
	
	/*
	 * update familiarity network based on interacting with an agent
	 */
	public void updateFamiliar(familiarModel model, Agent a) {
		// check to see if there's an edge to this agent in the interaction network
		Edge e = model.interactNet.getEdge(this, a);
		// if this is null, add it
		if(e == null) {
			model.interactNet.addEdge(this, a, 1.0);
		} else {
			// otherwise, increment the value of the existing edge
			double v = (double) e.getInfo();
			model.interactNet.updateEdge(e, this, a, v+1);
		}
		// grab the resulting strength of the connection between the two agents
		double val = (double) model.interactNet.getEdge(this, a).getInfo();
		// check to see if there's an edge to this agent in the familiarity network
		Edge f = model.famNet.getEdge(this, a);
		if(f != null) {
			// update the strength of the connection to match the interaction network
			model.famNet.updateEdge(f, this, a, val);
			model.familiarity += 1;
		} else if(val > this.lrate) {
			// otherwise, if their connection is strong enough, determine if it should be familiar
			// if there are fewer than the maximum number of edges just add this agent
			if(model.famNet.getEdgesOut(this).numObjs+1 <= this.memory) {
				model.famNet.addEdge(this, a, val);
				model.edgecount++;
				model.familiarity += val;
			} else if(this.weakestlink != null && val > (double) this.weakestlink.getInfo()) {
				// otherwise, if this edge's rank is higher than the weakest, replace it
				model.familiarity += val - (double) this.weakestlink.getInfo();
				model.famNet.updateEdge(this.weakestlink, this, a, val);
				// TODO - maybe set weakestlink to null add an additional condition if weakest link is null to find a new weakest link
				// TODO - alternatively do this part in pruneNet
			}
		}
	}
	
	/*
	 * calculate the local clustering coefficient for this agent and add to the total
	 */
	public void calcCluster(familiarModel model) {
		// TODO - calculate this based on another constant network that isn't dependent on individual parameters?
		// and set this agent's local cluster to zero
		this.localcluster = 0;
		// get all the neighbors of this node
		Bag neighbors = model.famNet.getEdgesOut(this);
		// use the modified network to calculate the new clustering coefficient if this agent has more than 1 neighbor
		if(neighbors.numObjs > 1) {
			// store number of connected neighbors
			double connections = 0;
			// loop through them and determine if they're connected to each other
			for(int i = 0; i < neighbors.numObjs; i++) {
				for(int j = 0; j < neighbors.numObjs; j++) {
					// store the edges
					Edge ei = (Edge) neighbors.get(i);
					Edge ej = (Edge) neighbors.get(j);
					// if there they're not the same agent, check to see if they're connected
					if(i != j && model.famNet.getEdge(ei.getTo(), ej.getTo()) != null) {
						connections++;
					}
				}
			}
			// divide by number of possible edges to get the clustering coefficient
			// and divide by the population size to average
			double total = neighbors.numObjs*(neighbors.numObjs-1)*model.popsize;
			this.localcluster = connections/total;
			// add to the population average
			model.clustering += this.localcluster;
		}
	}
	
	/*
	 * handles evolution (reproduction and death)
	 */
	public void evolve(familiarModel model) {
		// if it's reached the reproduction threshold, try to reproduce
		if(this.fitness >= model.reprothresh) {
			// decrease fitness whether it succeeds or not
			this.fitness -= model.reprothresh*model.reprocost;
			// if the actual population size is smaller than the threshold, actually reproduce
			if(model.popsize < model.popthresh) {
				reproduce(model);
			}
		}
		// if it's passed its lifespan, or there's strong selection and it's run out of fitness, this agent dies
		if(this.age > this.lifespan || (model.strongselect && this.fitness < 0)) {
			die(model);
		}
	}
	
	/*
	 * handles the actual reproduction, so that I can call this in a few different places
	 */
	public Agent reproduce(familiarModel model) {
		// increment the number of offspring this individual has had
		this.offspring++;
		// initialize list of traits
		double[] newtraits = new double[] {this.famBias, this.memory, this.lrate, this.decay, 0};
		// if familiarity is evolving, agents inherit their traits from their parent with some chance of mutation
		if(model.evolfam) {
			newtraits = model.varyGene(new double[] {this.famBias, this.memory, this.lrate, this.decay, 0}, 1, model.mutrate);
		} else if(model.initfamvar > 0) {
			// otherwise, just use the initial values for the model with some random variance
			newtraits = model.varyGene(new double[] {model.famBias, model.memory, model.lrate, model.decay, 0}, model.initfamvar, 1);
		}
		// if cooperation evolves, base that off of its parents with some chance of mutation too
		if(model.evolcoop) {
			boolean mutate = model.random.nextBoolean(model.mutrate);
			if((this.coop && !mutate) || (!this.coop && mutate)) {
				newtraits[4] = 1;
			}
		} else {
			// otherwise just draw randomly based on the initial proportion
			if(model.random.nextBoolean(model.coopprop)) newtraits[4] = 1;
			else newtraits[4] = 0;
		}
		// create a new agent with this agent's location as the origin
		Agent a = model.createAgent(newtraits, this.netx, this.nety);
		// give it its parent's aggregation tendency (with some mutation if it's evolving)
		a.viewrange = this.viewrange;
		if(model.evolagg) {
			a.viewrange = model.drawRange(this.viewrange, model.aggnoise, 1, Double.MAX_VALUE);
			model.agg[(a.coop) ? 1:0] += a.viewrange;
		}
		return a;
	}
	
	/*
	 * handles the actual death so I can call this in a few places
	 */
	public void die(familiarModel model) {
		// remove it from the population size
		model.popsize--;
		// and the list of agents
		model.agents[this.id] = null;
		// and add its id to the list of empty slots
		model.agentslots.add(this.id);
		// remove it from and the schedule
		this.stopbutton.stop();
		// and the space 
		model.agentNet.remove(this);
		// and both networks (but first the edge count)
		model.edgecount -= model.famNet.getEdgesIn(this).numObjs;
		model.edgecount -= model.famNet.getEdgesOut(this).numObjs;
		model.interactNet.removeNode(this);
		model.famNet.removeNode(this);
		// and the population averages
		model.famCount -= this.famCount;
		if(this.coop) model.coopCount--;
		// I need whether this individual is a cooperator as a number in a few palces
		int i = (this.coop) ? 1:0;
		// and the trait averages by strategy
		model.fbias[i] -= this.famBias;
		model.fmem[i] -= this.memory;
		model.fthresh[i] -= this.lrate;
		model.fdecay[i] -= this.decay;
		if(model.evolagg) model.agg[i] -= this.viewrange;
		// and add its information to the running totals (also by strategy)
		model.acount[i]++;
		model.interacts[i] += this.interact;
		model.fecundity[i] += this.offspring;
		model.survival[i] += this.age;
	}
}
