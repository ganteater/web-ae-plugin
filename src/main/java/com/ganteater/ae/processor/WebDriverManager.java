package com.ganteater.ae.processor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.WebDriver;

import com.ganteater.ae.AEWorkspace;

public class WebDriverManager {

	static {
		AEWorkspace.getInstance().addCloseHook(WebDriverManager::close);
	}

	private static Map<String, WebDriver> driverMap = new HashMap<>();

	public static WebDriver getDriver(String driverName) {
		return driverMap.get(driverName);
	}

	public static WebDriver setDriver(String driverName, WebDriver webDriver) {
		return driverMap.put(driverName, webDriver);
	}

	static void close() {
		Collection<WebDriver> values = driverMap.values();
		for (WebDriver webDriver : values) {
			try {
				webDriver.close();
			} catch (Exception e) {
				System.err.println(e.getMessage());
			}
			try {
				webDriver.quit();
			} catch (Exception e) {
				System.err.println(e.getMessage());
			}
		}
	}
}
