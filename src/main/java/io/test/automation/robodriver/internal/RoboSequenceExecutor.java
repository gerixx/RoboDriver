package io.test.automation.robodriver.internal;

import java.awt.GraphicsDevice;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.selenium.interactions.Sequence;

import io.test.automation.robodriver.RoboDriverCommandExecutor;

public class RoboSequenceExecutor extends Thread {
	
	private static Logger LOGGER = LoggerUtil.get(RoboDriverCommandExecutor.class);

	private Sequence seq;
	private Object tickLock = new Object();
	private boolean done;

	public RoboSequenceExecutor(Sequence seq) {
		super("robo-sequence-" + seq.hashCode());
		this.seq = seq;
	}

	@Override
	public void run() {
		try {
			executeTickByTick();
		} catch (InterruptedException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
		} finally {
			done = true;
		}
	}

	@SuppressWarnings("unchecked")
	private void executeTickByTick() throws InterruptedException {
		synchronized (tickLock) {
			Map<String, Object> seqMap = seq.encode();
			LOGGER.log(Level.FINE, () -> String.format("ACTION sequence raw data: %s", seqMap));
			String seqType = (String) seqMap.get("type");
			List<Object> sequenceActionList = (List<Object>) seqMap.get("actions");
			GraphicsDevice device = null; // target device must be defined by one of the following actions
			int xElementScreenOffset = 0;
			int yElementScreenOffset = 0;
			for (Object actionObject : sequenceActionList) {
				tickLock.wait(10000);
				Map<String, Object> actionDetails = (Map<String, Object>) actionObject;
				LOGGER.log(Level.FINE, () -> {
					return String.format("[%s] action_details list: %s", seqType, actionDetails);
				});
				final Object targetObject = actionDetails.get("origin");
				if (targetObject == null) {
					LOGGER.log(Level.FINEST, ()->String.format("[%s] no screen device defined, using default screen.", seqType));
					device = RoboUtil.getDefaultDevice();
				} else if (targetObject instanceof RoboScreen) {
					device = ((RoboScreen) targetObject).getDevice();
				} else if (targetObject instanceof RoboScreenRectangle) {
					RoboScreenRectangle rect = (RoboScreenRectangle) targetObject;
					device = rect.getScreen().getDevice();
					xElementScreenOffset = rect.getX();
					yElementScreenOffset = rect.getY();
				} else {
					if (device == null) { // expected that device was determined by the 'origin' of a previous action
						throw new RuntimeException(String.format(
								"[%s] no device defined, maybe invalid target element type '%s', '%s' is needed.",
								seqType, targetObject.toString(), RoboScreen.class.getName()));
					}
				}
				final String actionDetailType = (String) actionDetails.get("type");
				LOGGER.log(Level.FINEST, () -> String.format("[%s] action_type = '%s'", seqType, actionDetailType));
				switch (actionDetailType) {
				// pointer actions
				case "pointerMove":
					Long tickDuration = (Long) actionDetails.get("duration");
					Integer movePosX = xElementScreenOffset + (Integer) actionDetails.get("x");
					Integer movePosY = yElementScreenOffset + (Integer) actionDetails.get("y");
					RoboUtil.mouseMove(device, tickDuration, movePosX, movePosY);
					break;
				case "pointerDown":
					RoboUtil.mouseDown(device);
					break;
				case "pointerUp":
					RoboUtil.mouseUp(device);
					break;
					// key actions
				case "pause":
					// nothing to do 
					break;
				case "keyDown":
					String value = (String) actionDetails.get("value");
					RoboUtil.keyDown(device, value.charAt(0));
					break;
				case "keyUp":
					value = (String) actionDetails.get("value");
					RoboUtil.keyUp(device, value.charAt(0));
					break;
				default:
					LOGGER.log(Level.FINE, () -> {
						return String.format("[%s] unknown_action type '%s'", seqType, actionDetailType);
					});
				}
			}
		}
	}
	
	public boolean tick() {
		if (done) {
			return false;
		}
		synchronized(tickLock) {
			tickLock.notifyAll();
		}
		return !done;
	}

}