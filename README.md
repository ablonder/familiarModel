<p align="center">
  <img src="https://github.com/ablonder/familiarModel/blob/main/video.gif" />
</p>

# Why do fish cooperate?

Cooperation is a hard question of evolution. Why help others at a cost to yourself?

This is especially puzzling in fish schools where you can easily ditch one sucker and move on to the next. However, my simulation suggests that ditching your friends might not be so easy, even for fish. If everyone around you forms cliques of friends, if you exploit your friends too much, you're likely to end up all alone. And surviving on your own is hard, especially for a fish.

Here you can see schools of cooperators (those blue circles) swimming around. Occasionally a defector (in red) shows up and you can see how quickly it ends up alone and dies.

</br>

I built this simulation in Java using the <a href="https://cs.gmu.edu/~eclab/projects/mason/">MASON</a> Multi-Agent Toolkit and my own <a href="https://github.com/ablonder/SimDataCollection">SimDataCollection</a> infrastructure.

I implemented the fishes' schooling behavior based on <a href="https://journals.plos.org/ploscompbiol/article?id=10.1371/journal.pcbi.1005732">previous work on cooperation in fish</a>, where each individual follows its neighbors according to simple rules. To enable the fish to form friendships, I added a social network that underlies the whole simulation. Connections between fish are strengthened whenever they interact and grow weaker if they drift apart and don't see each other for a while. And then, I implemented an evolutionary algorithm, so that only successful fish survive and reproduce.

Remarkably, cooperation was the winning strategy!

</br>

See README.pdf for the complete model write up.
