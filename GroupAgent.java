package familiarity;

public class GroupAgent {

	public double famBias;
	public boolean coop;
	
	/*
	 * Creates a new agent with the given genotype and adds it to a group
	 */
	GroupAgent(double[] genotype, FamiliarGroups model){
		famBias = genotype[0];
		coop = genotype[1] == 1;
		
	}
}
