package de.hpi.ddm.actors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.opencsv.CSVReader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestActorRef;
import akka.testkit.javadsl.TestKit;
import de.hpi.ddm.actors.Master.FinishedReadingMessage;
import de.hpi.ddm.actors.Master.StartMessage;
import de.hpi.ddm.configuration.Configuration;
import de.hpi.ddm.singletons.ConfigurationSingleton;
import de.hpi.ddm.singletons.DatasetDescriptorSingleton;
import de.hpi.ddm.structures.BloomFilter;
import de.hpi.ddm.systems.MasterSystem;

public class BatchReadingTest {

	static ActorSystem system;

	@Before
	public void setUp() throws Exception {
		final Configuration c = ConfigurationSingleton.get();

		final Config config = ConfigFactory
				.parseString("akka.remote.artery.canonical.hostname = \"" + c.getHost() + "\"\n"
						+ "akka.remote.artery.canonical.port = " + c.getPort() + "\n" + "akka.cluster.roles = ["
						+ MasterSystem.MASTER_ROLE + "]\n" + "akka.cluster.seed-nodes = [\"akka://"
						+ c.getActorSystemName() + "@" + c.getMasterHost() + ":" + c.getMasterPort() + "\"]")
				.withFallback(ConfigFactory.load("application"));

		system = ActorSystem.create(c.getActorSystemName(), config);
	}

	@After
	public void tearDown() throws Exception {
		TestKit.shutdownActorSystem(system);
	}

	@Test
	public void testReadingWholeFile() {
		// Tests if the message is correctly transmitted, but does not test if the
		// inter-process communication works.
		new TestKit(system) {
			{
				ActorRef collector = system.actorOf(Collector.props(), "collector");
				BloomFilter filter = new BloomFilter();
				ActorRef reader = system.actorOf(Reader.props(), "reader");
				final TestActorRef<Master> masterRef = TestActorRef.create(system, Master.props(reader, collector, filter), "master");
				final Master master = masterRef.underlyingActor();
				String[] line;
				List<String[]> csvContent = new ArrayList<String[]>();
				try {
					CSVReader csvReader = DatasetDescriptorSingleton.get().createCSVReader();
					while (((line = csvReader.readNext()) != null)) {
						csvContent.add(line);
					}
				} catch(Exception e){
					System.out.println(e);
					/* assertTrue(message, condition);
					assertTrue("Could not read csv to check. Test failed before started.", false);
					return null; */
				}

				within(Duration.ofSeconds(10), () -> {
					masterRef.tell(new StartMessage(), ActorRef.noSender());
					this.expectMsgClass(FinishedReadingMessage.class);
					assertEquals("The number of lines do not match.", (long)csvContent.size(), (long)master.getLines().size());
					for(int i = 0; i < csvContent.size(); ++i){
						assertArrayEquals(csvContent.get(i), master.getLines().get(i));
					}
					return null;
				});
			}
		};
	}

}
