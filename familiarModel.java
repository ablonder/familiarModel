package familiarity;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.SparseField;
import sim.field.continuous.Continuous2D;
import sim.field.grid.SparseGrid2D;
import sim.field.network.Edge;
import sim.field.network.Network;
import sim.util.Bag;

/*
 * Requires the MASON Multiagent simulation toolkit (Luke et al. 2005) to run.
 */
public class familiarModel extends Model {
	
	// number of agents in the population
	public double popsize;
	// maximum number of agents in the population
	public int popthresh;
	// list of empty slots in the list of agents in the superclass
	public ArrayList<Integer> agentslots;
	// range of vision
	public double viewrange;
	// range of vision as proportion of space
	public double viewp;
	// range at which agents are repulsed by each other
	public double repulserange;
	// repulsion range as proportion of view range
	public double repulsep;
	// range at which agents are chosen to play each other
	public double interactrange;
	// interaction range as proportion of space
	public double interactp;
	// whether agents are chosen randomly as opposed to based on the nearest
	public boolean randinteract;
	// maximum angle of rotation
	public double maxrotate;
	// step size (or velocity)
	public double stepsize;
	// error rate in movement
	public double error;
	// weight of flocking (flocking, aggregating, and continuing all add to 1)
	public double flockstr;
	// weight of continuing (persisting) in the same direction
	public double contstr;
	// whether the agents try to move randomly or just stay still if they try to move into an occupied spot (on a grid)
	public boolean randbounce;
	// whether the agents should always be moving (on a grid)
	public boolean alwaysmove;
	// maximum distance of offspring from their parents (actually a proportion of the total space dimensions)
	public double reprodist;
	// whether any evolution happens at all
	public boolean evol;
	// whether preference for familiars is allowed to evolve
	public boolean evolfam;
	// whether cooperation is allowed to evolve
	public boolean evolcoop;
	// whether aggregation evolves
	public boolean evolagg;
	// which game the agents are playing
	public char game;
	// if the game is only played once at the end
	public boolean oneshot;
	// how frequently evolution happens if that's the case
	public int gentime;
	// payoffs for the game
	public double fitcost;
	public double fitgain;
	// cost-benefit ratio (always between 0 and 1)
	public double costb;
	// fitness scaling factor to multiply the costs and benefits by (as proportion of the reproduction threshold)
	public double fitscale;
	// cost for aggregation (for the Joshi et al model)
	public double aggcost;
	// whether the agents die if they reach zero fitness
	public boolean strongselect;
	// how much fitness is gained/lost on each step
	public double otherfit;
	// other fitness as a normally drawn proportion of fitscale
	public double ofitp;
	// minimum lifespan
	public double minlifespan;
	// variation in lifespan (between 0 and 1 for fitting into my nice Erlang)
	public double varlifespan;
	// initial fitness
	public double initfitness;
	// initial fitness as a proportion of the threshold for reproduction
	public double initfitp;
	// fitness threshold for reproduction
	public double reprothresh;
	// amount of resources lost at reproduction (as proportion of the threshold for reproduction)
	public double reprocost;
	// amount of initial variance in familiarity parameters
	public double initfamvar;
	// mutation rate
	public double mutrate;
	// a separate mutation rate for aggregation
	public double aggnoise;
	// whether the network is included at all
	public boolean net;
	// how strongly agents favor familiar individuals (used to initialize and then keep track)
	public double famBias;
	// number of other agents an agent can remember at a time
	public double memory;
	// proportion of total maximum population that can be remembered at a time
	public double memp;
	// how many encounters it takes for an agent to become familiar
	public double lrate;
	// proportion of lifespan that it takes for agents to become familiar (accounting for decay)
	public double lrp;
	// how long agents have to go without an encounter to no longer be familiar
	public double decay;
	// total familiar parameters for each strategy
	public double[] fbias;
	public double[] fmem;
	public double[] fthresh;
	public double[] fdecay;
	// total aggregation tendency for each strategy (when that evolves)
	public double[] agg;
	// there are also a few things I want accumulated over the whole simulation, so I need to count the total number of agents of each type
	public double[] acount;
	// I want to get total number of interactions
	public double[] interacts;
	// total fecundity
	public double[] fecundity;
	// and actual lifespan
	public double[] survival;
	// initial proportion of cooperative individuals
	public double coopprop;
	// average number of individuals an agent considers to be familiar
	public double famCount;
	// number of cooperative individuals
	public int coopCount;
	// whether the agents are on a grid or continuous space
	public boolean cont;
	// how large the space is (presumed square)
	public int dims;
	// the population density (proportion of number of squares) if population threshold isn't specified
	public double density;
	// burn in period with no mutation to allow agents to form groups first
	public int burnin;
	// space for the agents
	public SparseField agentNet;
	// underlying network of all agents who interact
	public Network interactNet;
	// social network of agents
	public Network famNet;
	// distance to use for the adjacency network (only keeps track of a network if it's greater than 0)
	public int adjacency;
	// spatial network of adjacent agents (just for data collection purposes)
	public Network adjNet;
	// clustering coefficient
	public double clustering;
	// average familiarity
	public double familiarity;
	// total number of edges
	public double edgecount;
	// whether to display the network on GUI runs
	public boolean displayNet;
	
	public familiarModel() {
		super();
	}
	
	public familiarModel(String fname) {
		super(fname);
	}
	
	public void start() {
		super.start();
		// there are some parameters whose values may need to be recalculated based on other parameters
		if(this.density > 0) this.popthresh = (int) (this.dims*this.dims*this.density);
		if(this.viewp > 0) this.viewrange = this.dims*this.viewp;
		if(this.repulsep > 0) this.repulserange = this.viewrange*this.repulsep;
		if(this.interactp > 0) this.interactrange = this.interactp*this.dims;
		if(this.costb > 0 && this.fitscale > 0) {
			this.fitgain = this.fitscale*this.reprothresh;
			this.fitcost = this.fitgain*this.costb;
			if(ofitp != 0) {
				this.otherfit = this.fitgain*this.ofitp;
				if(this.fitgain - this.fitcost + this.otherfit < 0) {
					this.otherfit = -this.random.nextDouble()*(this.fitgain-this.fitcost);
					// since I'm randomly drawing again, I need to change it for all the following replicates
					resetParam("ofitp", Double.toString(this.otherfit/this.fitgain));
				}
			}
		}
		if(this.memp > 0) this.memory = this.memp*this.popthresh;
		if(this.lrp > 0) this.lrate = this.lrp*(1-this.decay)*this.minlifespan;
		if(this.initfitp > 0) this.initfitness = this.reprothresh*this.initfitp;
		// restart the schedule
		this.schedule.clear();
		// initialize population size to zero
		this.popsize = 0;
		// initialize array of agents in the super class to an array of size equal to the population threshold
		this.agents = new Object[this.popthresh];
		// initialize available slots to an empty list
		this.agentslots = new ArrayList<Integer>();
		// initialize clustering to 0
		this.clustering = 0;
		// initialize average number of familiar individuals
		this.famCount = 0;
		// number of cooperators
		this.coopCount = 0;
		// intialize average familiarity to 0
		this.familiarity = 0;
		// initialize the interaction network
		this.interactNet = new Network();
		// and the familiarity network
		this.famNet = new Network();
		// initialize the space (either continuous or as a grid
		if(this.cont) {
			// I'm going to start with a discretization 1
			this.agentNet = new Continuous2D(1, this.dims, this.dims);
		} else {
			this.agentNet = new SparseGrid2D(this.dims, this.dims);
		}
		// store evolving parameters
		double[] gene = new double[] {this.famBias, this.memory, this.lrate, this.decay, 0};
		// initialize trait totals by strategy
		this.fbias = new double[] {0,0};
		this.fmem = new double[] {0,0};
		this.fthresh = new double[] {0,0};
		this.fdecay = new double[] {0,0};
		this.agg = new double[] {this.viewrange*(1-this.coopprop)*this.popthresh, this.viewrange*this.coopprop*this.popthresh};
		// also total number of interactions, fecundity, survival, and total number of agents
		this.interacts = new double[] {0,0};
		this.fecundity = new double[] {0,0};
		this.survival = new double[] {0,0};
		this.acount = new double[] {0,0};
		// initialize a population of agents
		for(int i = 0; i < this.popthresh; i++) {
			// create a new gene for this agent by copying the template
			double[] newgene = gene.clone();
			// if this agent is going to be a cooperator, set the last gene to 1
			if(i < this.coopprop*this.popthresh) newgene[4] = 1;
			// if there is initial variance, add some to each of the familiarity parameters
			if(this.initfamvar > 0) newgene = varyGene(newgene, this.initfamvar, 1);
			// create a new agent with this genotype and a random location
			Agent a = this.createAgent(newgene, random.nextInt(this.dims), random.nextInt(this.dims));
			// reinitialize its age from a uniform distribution from zero to its lifespan
			a.age = this.random.nextInt(a.lifespan);
			// add to its fitness from a uniform distribution between the initial fitness and the threshold
			a.fitness += this.random.nextDouble()*(this.reprothresh-this.initfitness);
			// if aggregative tendency is evolving, initialize that too
			a.viewrange = this.viewrange;
			// add it to the list of agents in the super class and initialize its ID number to i
			this.agents[i] = a;
			a.id = i;
		}
		// also add oneshot evolution to the schedule, if that's what's going on
		if(this.oneshot) {
			this.schedule.scheduleRepeating(new oneshotEvol(), gentime);
		}
	}
	
	/*
	 * Helper function so that I don't have to do mutation/add variance in multiple places
	 */
	public double[] varyGene(double[] gene, double var, double rate) {
		// loop through the familiarity traits to mutate them (only if variance is > 0)
		if(var > 0) {
			for(int i = 0; i < 4; i++) {
				// check to see if variance is added given the rate
				if(this.random.nextBoolean(rate)) {
					// for famBias and decay, draw from a Beta distribution (with a maximum of 3/4 variance to keep it a little tighter)
					if(i == 0 || i == 3) {
						gene[i] = drawBeta(gene[i], var*3/4);
					} else {
						// otherwise, for memory and lrate, draw from a Gamma distribution
						gene[i] = drawGamma(gene[i], var, 0);
					}
				}
			}
		}
		return gene;
	}
	
	/*
	 * Whatever class extends familiarModel will create a new agent of the right type with the given genotype and coordinates
	 */
	public Agent createAgent(double[] genotype, double x, double y) {
		// calculate the actual reproduction distance in this space
		double dist = this.reprodist*this.dims;
		// use the coordinates provided as the origin and choose a random location within the provided reproduction distance (adjusted toroidally)
		if(this.cont) {
			Continuous2D space = (Continuous2D) this.agentNet;
			return new SpatialAgent(this, genotype, this.initfitness, space.stx(x+random.nextDouble()*dist), space.sty(y+random.nextDouble()*dist));
		} else {
			SparseGrid2D space = (SparseGrid2D) this.agentNet;
			return new SpatialAgent(this, genotype, this.initfitness, space.stx((int) x+random.nextInt((int)dist)), space.sty((int) y+random.nextInt((int) dist)));
		}
	}
	
	/*
	 * I'll designate this as the class to pull parameters from
	 * @see familiarity.Model#setSubclass()
	 */
	public void setClasses() {
		this.subclass = familiarModel.class;
		this.agentclass = Agent.class;
	}
	
	/*
	 * this is where I'll set the parameters to initialize and pull
	 * @see familiarity.Model#setNames()
	 */
	public void setNames() {
		super.setNames();
		this.autoparams = true;
		this.autores = true;
	}
	
	/*
	 * returns results that I don't just want to be handled automatically
	 * @see familiarity.Model#getResult(java.lang.String, java.lang.Object, java.lang.Class)
	 */
	public String getResult(String r, Object o, Class c) {
		if(c == this.subclass) {
			switch(r) {
			case "meanfamBias": return(String.valueOf((this.fbias[1]/this.popsize) + (this.fbias[0]/this.popsize)));
			case "meanmemory": return(String.valueOf((this.fmem[1]/this.popsize) + (this.fmem[0]/this.popsize)));
			case "meanlrate": return(String.valueOf((this.fthresh[1]/this.popsize) + (this.fthresh[0]/this.popsize)));
			case "meandecay": return(String.valueOf((this.fdecay[1]/this.popsize) + (this.fdecay[0]/this.popsize)));
			case "familiarity": return(String.valueOf(this.familiarity/this.edgecount));
			case "coopFamBias": return(String.valueOf(this.fbias[1]/this.coopCount));
			case "coopMemory": return(String.valueOf(this.fmem[1]/this.coopCount));
			case "coopLrate": return(String.valueOf(this.fthresh[1]/this.coopCount));
			case "coopDecay": return(String.valueOf(this.fdecay[1]/this.coopCount));
			case "defectFamBias": return(String.valueOf(this.fbias[0]/(this.popsize-this.coopCount)));
			case "defectMemory": return(String.valueOf(this.fmem[0]/(this.popsize-this.coopCount)));
			case "defectLrate": return(String.valueOf(this.fthresh[0]/(this.popsize-this.coopCount)));
			case "defectDecay": return(String.valueOf(this.fdecay[0]/(this.popsize-this.coopCount)));
			case "coopCohesion": return(String.valueOf(this.agg[1]/this.coopCount));
			case "defectCohesion": return(String.valueOf(this.agg[0]/(this.popsize-this.coopCount)));
			case "coopInteracts": return(String.valueOf(this.interacts[1]/this.acount[1]));
			case "defectInteracts": return(String.valueOf(this.interacts[0]/this.acount[0]));
			case "coopFecundity": return(String.valueOf(this.fecundity[1]/this.acount[1]));
			case "defectFecundity": return(String.valueOf(this.fecundity[0]/this.acount[0]));
			case "coopSurvival": return(String.valueOf(this.survival[1]/this.acount[1]));
			case "defectSurvival": return(String.valueOf(this.survival[0]/this.acount[0]));
			// check for clustering so it's only calculated if needed
			case "clustering":
				// reset clustering back to zero
				this.clustering = 0;
				// loop through all the agents
				for(Object obj : this.agents) {
					// make sure it isn't null
					if(obj != null) {
						// cast as an agent
						Agent a = (Agent) obj;
						// calculate clustering for that agent
						a.calcCluster(this);
					}
				}
				// return the total clustering coefficient
				return(String.valueOf(this.clustering));
			}
		} else if(c == this.agentclass) {
			// for now, I'm just going to check to see if it's asking for the edge list
			switch(r) {
			// check for this agent's clustering coefficient
			case "clustering":
				// cast the object as an agent
				Agent a = (Agent) o;
				// calculate its local cluster
				a.calcCluster(this);
				// return it
				return(String.valueOf(a.localcluster));
			// if it wants the object, return the toString of the provided object
			case "obj":
				return("" + o);
			case "xdir":
			case "ydir":
				// this needs to be a spatial agent for it to work
				SpatialAgent s = (SpatialAgent) o;
				// return the x and y orientation respecitvely
				if(r.equals("xdir")) return("" + s.movedir[0]);
				else return("" + s.movedir[1]);
			// this gives it the option to return the number of individuals within the provided adjacency range
			case "neighbors":
				// again it needs to be a spatial agent for this to work
				s = (SpatialAgent) o;
				// each agent will calculate how many neighbors it has
				return("" + s.getAdjacency(this, false));
			}
		}
		return(super.getResult(r, o, c));
	}
	
	/*
	 * A simple steppable class in here to handle the oneshot evolution (only called every gentime steps)
	 */
	class oneshotEvol implements Steppable{
		public void step(SimState state) {
			// cast the state as a model
			familiarModel model = (familiarModel) state;
			// loop through all the agents in the model to calculate their fitness, since some are negative, I'll get the min to add to make all positive
			double minfit = 0;
			for(int i = 0; i < model.popthresh; i++) {
				// grab the next agent from the population (I'm going to assume it isn't null)
				Agent a = (Agent) model.agents[i];
				// have it play to calculate its fitness (if it hasn't played before)
				if(a.interact == 0) a.playGame(model, null);
				// check to see if it's less than the min
				if(a.fitness < minfit) minfit = a.fitness;
			}
			// then get the cumulative fitness, normalized for minimum, gathering the total to generate a range to draw from
			double totalfit = 0;
			double[] fitness = new double[(int) model.popthresh];
			for(int i = 0; i < model.popthresh; i++) {
				Agent a = (Agent) model.agents[i];
				// add in normalized so it's all greater than 1
				totalfit += a.fitness - minfit + 1;
				fitness[i] = totalfit;
			}
			// then draw until we have a whole new population of agents
			Object[] newagents = new Object[(int) model.popthresh];
			for(int j = 0; j < model.popthresh; j++) {
				// draw a number between 0 and totalfit
				double draw = model.random.nextDouble()*totalfit;
				// go through the cumulative fitness list until it reaches an individual whose fitness is greater
				int k = 0;
				while(fitness[k] < draw && k < model.popthresh-1) k++;
				// the new value of k should be the right agent to reproduce
				Agent a = ((Agent) model.agents[k]).reproduce(model);
				// add it to the list of new agents
				newagents[j] = a;
				// and give it an id
				a.id = j;
			}
			// at the end, delete all the old agents and replace them with the new
			for(int k = 0; k < model.popthresh; k++) {
				((Agent) model.agents[k]).die(model);
			}
			model.agents = newagents;
		}
	}
	
	
	public static void main(String[] args) {
		familiarModel model = new familiarModel("neighbortest.txt");
	}
}
