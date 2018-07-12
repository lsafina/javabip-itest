/*
 * Copyright 2012-2016 École polytechnique fédérale de Lausanne (EPFL), Switzerland
 * Copyright 2012-2016 Crossing-Tech SA, Switzerland
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Simon Bliudze, Anastasia Mavridou, Radoslaw Szymanek and Alina Zolotukhina
 */
package org.javabip.executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Random;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.RoutePolicy;
import org.javabip.spec.*;
import org.javabip.api.BIPActor;
import org.javabip.api.BIPEngine;
import org.javabip.api.BIPGlue;
import org.javabip.api.PortBase;
import org.javabip.api.PortType;
import org.javabip.engine.factory.EngineFactory;
import org.javabip.exceptions.BIPException;
import org.javabip.executor.PortImpl;
import org.javabip.glue.GlueBuilder;
import org.javabip.glue.TwoSynchronGlueBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import akka.actor.ActorSystem;

public class IntegrationTests {

	ActorSystem system;
	EngineFactory engineFactory;

	@Before
	public void initialize() {

		system = ActorSystem.create("MySystem");
		engineFactory = new EngineFactory(system);

	}

	@After
	public void cleanup() {

		system.shutdown();

	}

	private BIPGlue createGlue(String bipGlueFilename) {
		BIPGlue bipGlue = null;

		InputStream inputStream;
		try {
			inputStream = new FileInputStream(bipGlueFilename);

			bipGlue = GlueBuilder.fromXML(inputStream);

		} catch (FileNotFoundException e) {

			e.printStackTrace();
		}
		return bipGlue;

	}

	@Test
	public void bipGlueTest() {
		BIPGlue bipGlue = createGlue("src/test/resources/bipGlueExecutableBehaviour.xml");
		assertEquals("The number of accept constraints is not appropriate", 5, bipGlue.getAcceptConstraints().size());
		assertEquals("The number of require constraints is not appropriate", 5, bipGlue.getRequiresConstraints().size());
	}

	@Test
	public void hashCodePortTest() {
		PortBase portA = new PortImpl("p", PortType.enforceable, SwitchableRoute.class);
		PortBase portB = new PortImpl("p", PortType.enforceable, SwitchableRoute.class);
		PortBase portC = new PortImpl("p", PortType.enforceable, SwitchableRoute.class);
		PortBase portD = new PortImpl("p", PortType.enforceable, SwitchableRoute.class);

		assertEquals(portA.hashCode(), portB.hashCode());
		assertEquals(portC.hashCode(), portD.hashCode());
	}

	@Test
	public void routesTest() throws BIPException {

		/*
		 * Test story.
		 * 
		 * The classical Switchable Routes example with three routes and one monitor, without data transfer,
		 * specification through annotations.
		 */

		BIPGlue bipGlue = new TwoSynchronGlueBuilder() {
			@Override
			public void configure() {

				synchron(SwitchableRoute.class, "on").to(RouteOnOffMonitor.class, "add");
				synchron(SwitchableRoute.class, "finished").to(RouteOnOffMonitor.class, "rm");

//				port(SwitchableRoute.class, "on").acceptsNothing();
//				port(SwitchableRoute.class, "on").requiresNothing();
//
//				port(SwitchableRoute.class, "finished").acceptsNothing();
//				port(SwitchableRoute.class, "finished").requiresNothing();

				port(SwitchableRoute.class, "off").acceptsNothing();
				port(SwitchableRoute.class, "off").requiresNothing();

			}
		}.build();

		BIPEngine engine = engineFactory.create("myEngine", bipGlue);

		SwitchableRoute route1 = new SwitchableRoute("1");
		SwitchableRoute route2 = new SwitchableRoute("2");
		SwitchableRoute route3 = new SwitchableRoute("3");
		SwitchableRoute route4 = new SwitchableRoute("4");
		RouteOnOffMonitor routeOnOffMonitor = new RouteOnOffMonitor(2);

		CamelContext camelContext = new DefaultCamelContext();
		route1.setCamelContext(camelContext);
		route2.setCamelContext(camelContext);
		route3.setCamelContext(camelContext);
		route4.setCamelContext(camelContext);

		final BIPActor executor1 = engine.register(route1, "1", true);
		final BIPActor executor2 = engine.register(route2, "2", true);
		final BIPActor executor3 = engine.register(route3, "3", true);
		final BIPActor executor4 = engine.register(route4, "4", true);

		@SuppressWarnings("unused")
		final BIPActor executorM = engine.register(routeOnOffMonitor, "monitor", true);

		final RoutePolicy routePolicy1 = createRoutePolicy(executor1);
		final RoutePolicy routePolicy2 = createRoutePolicy(executor2);
		final RoutePolicy routePolicy3 = createRoutePolicy(executor3);
		final RoutePolicy routePolicy4 = createRoutePolicy(executor4);

		RouteBuilder builder = new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from("file:inputfolder1?delete=true").routeId("1").routePolicy(routePolicy1).to("file:inputfolder2");

				from("file:inputfolder2?delete=true").routeId("2").routePolicy(routePolicy2).to("file:inputfolder3");

				from("file:inputfolder3?delete=true").routeId("3").routePolicy(routePolicy3).to("file:inputfolder4");

				from("file:inputfolder4?delete=true").routeId("4").routePolicy(routePolicy4).to("file:inputfolder1");
			}
		};
		camelContext.setAutoStartup(false);

		try {
			camelContext.addRoutes(builder);
			camelContext.start();
		} catch (Exception e) {
			e.printStackTrace();
		}

		engine.specifyGlue(bipGlue);
		engine.start();
		engine.execute();

		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		executorM.inform("switch");

		while (!(executorM.getState().equals("0"))) {}

		engine.stop();
		engineFactory.destroy(engine);

        System.out.println(route1.noOfEnforcedTransitions);
        System.out.println(route2.noOfEnforcedTransitions);
        System.out.println(route3.noOfEnforcedTransitions);
		System.out.println(route4.noOfEnforcedTransitions);

		assertTrue("Route 1 has not made any transitions", route1.noOfEnforcedTransitions > 0);
		assertTrue("Route 2 has not made any transitions", route2.noOfEnforcedTransitions > 0);
		assertTrue("Route 3 has not made any transitions", route3.noOfEnforcedTransitions > 0);
		assertTrue("Route 4 has not made any transitions", route4.noOfEnforcedTransitions > 0);
	}

	@Test
	public void behaviourBuildingTest() throws BIPException {

		/*
		 * Test story.
		 * 
		 * The classical Switchable Routes example with three routes and one monitor, without data transfer,
		 * specification through provided behaviour.
		 */
		
		BIPGlue bipGlue = new TwoSynchronGlueBuilder() {
			@Override
			public void configure() {

				synchron(SwitchableRouteExecutableBehavior.class, "on").to(RouteOnOffMonitor.class, "add");
				synchron(SwitchableRouteExecutableBehavior.class, "finished").to(RouteOnOffMonitor.class, "rm");

				port(SwitchableRouteExecutableBehavior.class, "off").acceptsNothing();
				port(SwitchableRouteExecutableBehavior.class, "off").requiresNothing();

			}
		}.build();

		BIPEngine engine = engineFactory.create("myEngine", bipGlue);

		SwitchableRouteExecutableBehavior route1 = new SwitchableRouteExecutableBehavior("1");
		SwitchableRouteExecutableBehavior route2 = new SwitchableRouteExecutableBehavior("2");
		SwitchableRouteExecutableBehavior route3 = new SwitchableRouteExecutableBehavior("3");
		RouteOnOffMonitor routeOnOffMonitor = new RouteOnOffMonitor(2);

		CamelContext camelContext = new DefaultCamelContext();
		route1.setCamelContext(camelContext);
		route2.setCamelContext(camelContext);
		route3.setCamelContext(camelContext);

		final BIPActor executor1 = engine.register(route1, "1", false);
		final BIPActor executor2 = engine.register(route2, "2", false);
		final BIPActor executor3 = engine.register(route3, "3", false);

		@SuppressWarnings("unused")
		final BIPActor executorM = engine.register(routeOnOffMonitor, "monitor", true);

		final RoutePolicy routePolicy1 = createRoutePolicy(executor1);

		final RoutePolicy routePolicy2 = createRoutePolicy(executor2);

		final RoutePolicy routePolicy3 = createRoutePolicy(executor3);

		RouteBuilder builder = new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				from("file:inputfolder1?delete=true").routeId("1").routePolicy(routePolicy1).to("file:outputfolder1");

				from("file:inputfolder2?delete=true").routeId("2").routePolicy(routePolicy2).to("file:outputfolder2");

				from("file:inputfolder3?delete=true").routeId("3").routePolicy(routePolicy3).to("file:outputfolder3");
			}
		};
		camelContext.setAutoStartup(false);
		try {
			camelContext.addRoutes(builder);
			camelContext.start();
		} catch (Exception e) {
			e.printStackTrace();
		}

		engine.specifyGlue(bipGlue);
		engine.start();

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		engine.execute();

		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		engine.stop();
		engineFactory.destroy(engine);

		assertTrue("Route 1 has not made any transitions", route1.noOfEnforcedTransitions > 0);
		assertTrue("Route 2 has not made any transitions", route2.noOfEnforcedTransitions > 0);
		assertTrue("Route 3 has not made any transitions", route3.noOfEnforcedTransitions > 0);

	}

	@Test
	public void enforceableSpontaneousTest() throws BIPException {

		/*
		 * Test story.
		 * 
		 * There is one component (TestSpecEnforceableSpontaneous) with one enforceable port p and one spontaneous port
		 * s. The two ports must be executed in alternation, one after the other. A separate thread sends a fixed number
		 * of spontaneous events with a random frequency. The testing thread then sleeps every now and then until a
		 * certain number of sleeps has been reached. By then the component must have executed all spontaneous
		 * transitions.
		 */

		BIPGlue bipGlue = new GlueBuilder() {
			@Override
			public void configure() {

				port(TestSpecEnforceableSpontaneous.class, "p").requiresNothing();

				port(TestSpecEnforceableSpontaneous.class, "p").acceptsNothing();

			}

		}.build();

		BIPEngine engine = engineFactory.create("myEngine", bipGlue);

		final int noSpontaneousToBeSend = 5;
		final int noOfMilisecondsBetweenS = 1000;
		final int executorLoopDelay = 1000;

		TestSpecEnforceableSpontaneous component1 = new TestSpecEnforceableSpontaneous();

		final BIPActor executor1 = engine.register(component1, "comp1", true);

		Thread threadSendingSpontaneousEvents = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noSpontaneousToBeSend; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					executor1.inform("s");
				}

			}
		}, "SpontaneousSender");

		engine.specifyGlue(bipGlue);
		engine.start();

		threadSendingSpontaneousEvents.start();

		int sleepCounter = 0;

		engine.execute();

		while (component1.getsCounter() < noSpontaneousToBeSend) {
			final int internalSleep = 2000;
			try {
				Thread.sleep(internalSleep);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			sleepCounter++;
			if (sleepCounter > 2 * noOfMilisecondsBetweenS * noSpontaneousToBeSend / internalSleep + executorLoopDelay
					* noSpontaneousToBeSend * 2)
				fail("Not enough spontaneous events have been executed within a given time frame.");
		}

		engine.stop();
		engineFactory.destroy(engine);

		assertTrue("Component 1 has not made any transitions", component1.pCounter > 0);

		assertEquals(component1.getsCounter(), noSpontaneousToBeSend);

	}

	@Test
	public void enforceableSpontaneous2Test() throws BIPException {

		/*
		 * Test story.
		 * 
		 * There is one component (TestSpecEnforceableSpontaneous) with one enforceable port p and one spontaneous port
		 * s. The two ports must be executed in alternation, one after the other. A separate thread sends a fixed number
		 * of spontaneous events with a random frequency. The testing thread then sleeps every now and then until a
		 * certain number of sleeps has been reached. By then the component must have executed all spontaneous
		 * transitions.
		 * 
		 * The difference with the previous test is in sleep duration and frequency and that there is only one
		 * spontaneous transition.
		 */

		BIPGlue bipGlue = new GlueBuilder() {
			@Override
			public void configure() {

				port(TestSpecEnforceableSpontaneous.class, "p").requiresNothing();

				port(TestSpecEnforceableSpontaneous.class, "p").acceptsNothing();

			}

		}.build();

		BIPEngine engine = engineFactory.create("myEngine", bipGlue);

		final int noSpontaneousToBeSend = 1;
		final int noOfMilisecondsBetweenS = 1000;

		TestSpecEnforceableSpontaneous component1 = new TestSpecEnforceableSpontaneous();

		final BIPActor executor1 = engine.register(component1, "comp1", true);

		Thread t2 = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noSpontaneousToBeSend; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					executor1.inform("s");
				}

			}
		}, "SpontaneousSender");

		engine.specifyGlue(bipGlue);
		engine.start();

		t2.start();

		int sleepCounter = 0;

		engine.execute();

		try {
			Thread.sleep(30000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		while (component1.getsCounter() < noSpontaneousToBeSend) {
			final int internalSleep = 2000;
			try {
				Thread.sleep(internalSleep);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			sleepCounter++;
			if (sleepCounter > 2 * noOfMilisecondsBetweenS * noSpontaneousToBeSend / internalSleep)
				fail("Not enough spontaneous events have been executed within a given time frame: "
						+ component1.getsCounter());
		}

		engine.stop();
		engineFactory.destroy(engine);

		assertTrue("Component 1 has not made any transitions", component1.pCounter > 0);

		assertEquals(component1.getsCounter(), noSpontaneousToBeSend);

	}

	@Test
	public void ternaryInteractionWithTriggerTest() throws BIPException {

		/*
		 * Test story.
		 * 
		 * Rcomponent with its port triggers an interaction where pComponent (p port) is synchronized with qComponent (q
		 * port)
		 * 
		 * In Q: s enables q, after executing q gets disabled. In P: either p does not need to be globally enabled, or s
		 * enables p, after executing p gets disabled.
		 * 
		 * pComponent because it is initialized with false will not be able to execute transitions with spontaneous
		 * events. What happens then with BIP Executor? Lets assume for a moment that queuing behavior is correct. P is
		 * always enabled. Therefore, the maximum interaction will always choose pqr (or pr), and not just qr.
		 * 
		 * Components q and r have to have spontaneous events received and treated to be able to have enforceable
		 * transition enabled.
		 */

		BIPGlue bipGlue = new GlueBuilder() {
			@Override
			public void configure() {

				port(PComponent.class, "p").requires(RComponent.class, "r");

				port(PComponent.class, "p").accepts(QComponent.class, "q", RComponent.class, "r");

				port(QComponent.class, "q").requires(PComponent.class, "p");

				port(QComponent.class, "q").accepts(PComponent.class, "p", RComponent.class, "r");

				port(RComponent.class, "r").accepts(PComponent.class, "p", QComponent.class, "q");

				port(RComponent.class, "r").requiresNothing();

			}

		}.build();

		final int noIterations = 5;
		final int noOfMilisecondsBetweenS = 1000;
		// final int executorLoopDelay = 1000;

		BIPEngine engine = engineFactory.create("myEngine", bipGlue);

		// TODO, make it into a separate test that does test round trip to-from xml.
		ByteArrayOutputStream bipGlueOutputStream = new ByteArrayOutputStream();

		bipGlue.toXML(bipGlueOutputStream);

		ByteArrayInputStream bipGlueInputStream = new ByteArrayInputStream(bipGlueOutputStream.toByteArray());

		bipGlue = GlueBuilder.fromXML(bipGlueInputStream);

		// Component P that does not need enable signals.

		PComponent pComponent = new PComponent(false);
		final BIPActor pExecutor = engine.register(pComponent, "pComponent", true);

		// Component Q

		QComponent qComponent = new QComponent();
		final BIPActor qExecutor = engine.register(qComponent, "qComponent", true);

		// Component R

		RComponent rComponent = new RComponent();
		final BIPActor rExecutor = engine.register(rComponent, "rComponent", true);

		Thread testDriver = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noIterations; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					// This spontaneous signal can be a bit problematic as the
					// guard is never true,

					pExecutor.inform("s");
					rExecutor.inform("s");
					qExecutor.inform("s");
				}

			}
		}, "TestDriver");

		engine.specifyGlue(bipGlue);
		engine.start();

		testDriver.start();

		engine.execute();

		try {
			Thread.sleep(40000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		engine.stop();
		engineFactory.destroy(engine);

		assertEquals(noIterations, pComponent.pCounter);
		assertEquals(noIterations, rComponent.rCounter);

		assertTrue(qComponent.qCounter <= noIterations);

	}

	@Test
	public void mistakeInWiringTest() throws BIPException {

		/*
		 * 
		 * In place annotated with comment "MISTAKE on purpose", we use the second time pComponent instead of q
		 * component to get ArrayIndexOutOfBoundException.
		 */

		BIPGlue bipGlue = new GlueBuilder() {
			@Override
			public void configure() {

				port(PComponent.class, "p").requires(RComponent.class, "r");

				port(PComponent.class, "p").accepts(QComponent.class, "q", RComponent.class, "r");

				port(QComponent.class, "q").requires(RComponent.class, "r");

				port(QComponent.class, "q").accepts(PComponent.class, "p", RComponent.class, "r");

				port(RComponent.class, "r").accepts(PComponent.class, "p", QComponent.class, "q");

				port(RComponent.class, "r").requiresNothing();

			}

		}.build();

		final int noIterations = 5;
		final int noOfMilisecondsBetweenS = 1000;

		BIPEngine engine = engineFactory.create("myEngine", bipGlue);

		// Component P that does not need enable signals.

		PComponent pComponent = new PComponent(false);
		final BIPActor pExecutor = engine.register(pComponent, "pComponent", true);

		// Component Q

		// QComponent qComponent = new QComponent();
		// MISTAKE on purpose, so the above line can be commented out.
		final BIPActor qExecutor = engine.register(pComponent, "qComponent", true);

		// Component R

		RComponent rComponent = new RComponent();
		final BIPActor rExecutor = engine.register(rComponent, "rComponent", true);

		Thread testDriver = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noIterations; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					// This spontaneous signal can be a bit problematic as the
					// guard is never true,

					pExecutor.inform("s");

					qExecutor.inform("s");

					rExecutor.inform("s");
				}

			}
		}, "TestDriver");

		engine.specifyGlue(bipGlue);
		engine.start();

		testDriver.start();

		engine.execute();

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		engine.stop();
		engineFactory.destroy(engine);

		assertEquals("No progress for pComponent due to lack of QComponent", 0, pComponent.pCounter);
		assertEquals("No progress for rComponent due to lack of QComponent", 0, rComponent.rCounter);

	}

	@Test
	public void multipleSpontaneousTest() throws BIPException {

		/*
		 * Test story.
		 * 
		 * A synchronous interaction PSS.p with R.r
		 * 
		 * PSS component receives two spontaneous events that are changing the status of variable used as p guard. There
		 * are twice more enabling events than disabling events so still noIterations of pr interactions should take
		 * place.
		 * 
		 * R.s is also being sent so that R.r can be enabled.
		 * 
		 * [DONE] This test assumes a new treatment of spontaneous events, namely that if one spontaneous event has
		 * arrived then if its guard evaluates to true (or guard does not exist) then there is no exception if
		 * enforceable transition also evaluates to true. Spontaneous transition will have a precedence over enforceable
		 * one.
		 */

		final int noIterations = 5;
		final int noOfMilisecondsBetweenS = 10;
		final int executorLoopDelay = 1000;

		BIPGlue bipGlue = new GlueBuilder() {
			@Override
			public void configure() {

				port(PSSComponent.class, "p").requires(RComponent.class, "r");
				port(PSSComponent.class, "p").accepts(RComponent.class, "r");

				port(RComponent.class, "r").accepts(PSSComponent.class, "p");

				port(RComponent.class, "r").requires(PSSComponent.class, "p");

			}

		}.build();

		BIPEngine engine = engineFactory.create("myEngine", bipGlue);

		// Component P that does not need enable signals.

		PSSComponent pComponent = new PSSComponent(true);

		final BIPActor pExecutor = engine.register(pComponent, "pComponent", true);

		// Component R

		RComponent rComponent = new RComponent();

		final BIPActor rExecutor = engine.register(rComponent, "rComponent", true);

		Thread testDriver1 = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noIterations; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					// This spontaneous signal can be a bit problematic as the
					// guard is never true,

					pExecutor.inform("s1");

				}

			}
		}, "TestDriver1");

		Thread testDriver2 = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noIterations; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					// This spontaneous signal can be a bit problematic as the
					// guard is never true,

					pExecutor.inform("s1");

				}

			}
		}, "TestDriver2");

		Thread testDriver3 = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noIterations; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					// This spontaneous signal can be a bit problematic as the
					// guard is never true,

					pExecutor.inform("s2");

				}

			}
		}, "TestDriver3");

		Thread testDriver4 = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noIterations; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					// This spontaneous signal can be a bit problematic as the
					// guard is never true,

					rExecutor.inform("s");

				}

			}
		}, "TestDriver4");

		engine.specifyGlue(bipGlue);
		engine.start();

		testDriver1.start();
		testDriver2.start();
		testDriver3.start();
		testDriver4.start();

		int sleepCounter = 0;

		engine.execute();

		while (pComponent.pCounter < noIterations) {
			final int internalSleep = 20000;
			try {
				Thread.sleep(internalSleep);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			sleepCounter++;
			if (sleepCounter > 2 * noOfMilisecondsBetweenS * noIterations / internalSleep + executorLoopDelay
					* noIterations * 2 / internalSleep + 1)
				break;
		}

		engine.stop();
		engineFactory.destroy(engine);

		assertEquals(noIterations * 2, pComponent.spontaneousEnableCounter);
		assertEquals(noIterations, pComponent.spontaneousDisableCounter);
		assertEquals(noIterations, pComponent.pCounter);

	}

	@Test
	@Ignore
	// old ignore because the test is time-consuming
	public void binaryInteractionLargeBehaviorTest() throws NoSuchMethodException, BIPException {

		/*
		 * Test story.
		 * 
		 * PResizableComponent is a component with one enforceable transition and two spontaneous. SR does the rollback
		 * of enforceable transition. SE enables next p transition.
		 * 
		 * QComponent has q transition that participates in the interaction with p.
		 * 
		 * PResizable component has a large state space (e.g. 1000 states decided upon the construction).
		 * 
		 * 
		 * 
		 * Components q and r have to have spontaneous events recceived and treated to be able to have enforceable
		 * transition enabled.
		 */

		final int noIterations = 100; // no of states in PResizableComponent too.
		final int noOfMilisecondsBetweenS = 1000;
		final int executorLoopDelay = 1000;

		BIPGlue bipGlue = new GlueBuilder() {
			@Override
			public void configure() {

				port(PResizableBehaviorComponent.class, "p").requires(QComponent.class, "q");

				port(PResizableBehaviorComponent.class, "p").accepts(QComponent.class, "q");

				port(QComponent.class, "q").requires(PResizableBehaviorComponent.class, "p");

				port(QComponent.class, "q").accepts(PResizableBehaviorComponent.class, "p");

			}

		}.build();

		BIPEngine engine = engineFactory.create("myEngine", bipGlue);

		// Component P that does not need enable signals.

		PResizableBehaviorComponent pComponent = new PResizableBehaviorComponent(true, noIterations);

		final BIPActor pExecutor = engine.register(pComponent, "pComponent", false);

		// Component Q

		QComponent qComponent = new QComponent();

		final BIPActor qExecutor = engine.register(qComponent, "qComponent", true);

		// We enable noIterations of pq interactions.
		Thread testDriver = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noIterations; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					pExecutor.inform("se");

					qExecutor.inform("s");
				}

			}
		}, "TestDriver");

		// We force noIterations rollback on p component.
		Thread testDriver2 = new Thread(new Runnable() {

			public void run() {

				Random random = new Random(100);
				for (int i = 0; i < noIterations; i++) {
					try {
						Thread.sleep(random.nextInt(noOfMilisecondsBetweenS));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					/* This spontaneous signal has no guard so it will be always executed. */

					pExecutor.inform("sr");

				}

			}
		}, "TestDriver2");

		engine.specifyGlue(bipGlue);

		engine.start();

		testDriver.start();
		testDriver2.start();

		int sleepCounter = 0;

		engine.execute();

		while (qComponent.qCounter < noIterations) {
			final int internalSleep = 4000;
			try {
				Thread.sleep(internalSleep);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			sleepCounter++;
			if (sleepCounter > 2 * noOfMilisecondsBetweenS * noIterations / internalSleep + executorLoopDelay
					* noIterations * 2)
				fail("Not enough spontaneous events have been executed within a given time frame.");
		}

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		engine.stop();
		engineFactory.destroy(engine);

		assertEquals(0, pComponent.pCounter);

		assertEquals(noIterations, qComponent.qCounter);

	}

	private RoutePolicy createRoutePolicy(final BIPActor executor) {

		return new RoutePolicy() {

			public void onInit(Route route) {
			}

			public void onExchangeDone(Route route, Exchange exchange) {

				executor.inform("end");
			}

			public void onExchangeBegin(Route route, Exchange exchange) {
			}

			@Override
			public void onRemove(Route arg0) {
			}

			@Override
			public void onResume(Route arg0) {
			}

			@Override
			public void onStart(Route arg0) {
			}

			@Override
			public void onStop(Route arg0) {
			}

			@Override
			public void onSuspend(Route arg0) {
			}
		};

	}

}
