package com.ganteater.ae.processor;

import org.junit.Before;
import org.junit.Test;

import com.ganteater.ae.desktop.Anteater;

public class DesktopAEManualTest {

	@Before
	public void configuration() {
	}

	@Test
	public void testGUI() throws Exception {
		Anteater.main();
		System.in.read();
	}

}
