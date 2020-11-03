package familiarity;

import java.util.ArrayList;

/*
 * Evolution of preference for familiar individuals and cooperation in imposed group structure 
 */
public class FamiliarGroups extends familiarModel {

	// initial number of groups and count
	public int groupcount;
	// maximum group size before fission
	public int groupmax;
	// minimum group size before dispersal
	public int groupmin;
	// list of groups
	//public ArrayList<Group> groups = new ArrayList<Group>();
	
	public FamiliarGroups() {
		super();
	}
	
	public FamiliarGroups(String fname) {
		super(fname);
	}
	
	/*
	 * Add this subclass's attributes to the list of parameters
	 * @see familiarity.familiarModel#setNames()
	 */
	public void setNames() {
		super.setNames();
		this.paramnames = new String[] {"groupcount", "groupmax", "groupmin"};
	}
	
	/*
	 * @see familiarity.familiarModel#start()
	 */
	public void start() {
		super.start();
	}
	
	/*
	 * create new agents and assign them to groups (created as needed)
	 * @see familiarity.familiarModel#createAgent(double[])
	 */
	public void createAgent(double[] genotype) {
		new GroupAgent(genotype, this);
	}

}
