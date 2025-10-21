package com.ganteater.ae.processor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.map.LinkedMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Options;
import org.openqa.selenium.WebDriver.Window;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.ie.InternetExplorerOptions;
import org.openqa.selenium.remote.CapabilityType;

import com.ganteater.ae.AEWorkspace;
import com.ganteater.ae.CommandException;
import com.ganteater.ae.processor.annotation.CommandExamples;
import com.ganteater.ae.util.TestCase;
import com.ganteater.ae.util.xml.easyparser.Node;

public class Web extends BaseProcessor {

	private static final String SELECTOR_TARG_LIST = "<xpath>...</xpath><className>...</className><cssSelector>...</cssSelector><id>...</id><linkText>...</linkText><partialLinkText>...</partialLinkText><tagName>...</tagName>";
	private static final String MSEDGE = "msedge";
	private static final String GECKO = "gecko";
	private static final String CHROME = "chrome";

	private static final String PLUGINS_DIR_NAME = "plugins";
	private static final int SLEEP_INTERVAL = 50;
	private static final int ALERT_TIMEOUT = 500;
	private static final String DEFAULT_DRIVER_NAME = "default";
	private static final String WEB_DRIVER_TYPE_VAR_NAME = "WEB_DRIVER_TYPE";
	private static final String DEFAULT_DRIVER_TYPE = CHROME;

	private String driverName = DEFAULT_DRIVER_NAME;
	private long timeout = 1;
	private Web webParrentProcessor;

	private String type;
	private static Map<String, String> windowMap = new HashMap<>();

	public Web() {
		webParrentProcessor = getWebParrentProcessor();
		if (webParrentProcessor != null) {
			setDriver(webParrentProcessor.getDriver());
			timeout = webParrentProcessor.timeout;
		}
	}

	private Web getWebParrentProcessor() {
		Processor parent = getParent();
		while (parent != null && !(parent instanceof Web)) {
			parent = parent.getParent();
		}
		if (parent instanceof Web) {
			return (Web) parent;
		}
		return null;
	}

	@Override
	public void init(Processor aParent, Node action) throws CommandException {
		timeout = getTimeout(action);
		super.init(aParent, action);
		String name = attr(action, "name");
		driverName = StringUtils.defaultIfEmpty(name, driverName);

		if (getDriver() == null) {
			createDriver(action);
		}

		WebDriver driver = getDriver();

		String tab = attr(action, "tab");

		if (tab != null) {
			if (windowMap.isEmpty()) {
				String windowId = driver.getWindowHandle();
				windowMap.put(tab, windowId);

			} else {
				String windowId = windowMap.get(tab);

				if (windowId != null) {
					try {
						createTab(action, driver, tab, windowId);
					} catch (WebDriverException e) {
						windowMap.remove(tab);
						windowId = null;
					}
				}

				if (windowId == null) {
					try {
						driver.getCurrentUrl();
						createTab(driver, tab);
					} catch (WebDriverException e) {
						createDriver(action);
						driver = getDriver();
						windowMap.put(tab, driver.getWindowHandle());
					}

				}
			}
		}

		if (getDriver() != null) {
			try {
				Window window = getDriver().manage().window();
				if (window == null) {
					quiteDriver();
				}
				if (window != null) {
					window.getSize();
				}
			} catch (WebDriverException | NullPointerException e) {
				log.debug(e);
				quiteDriver();
			}
		}
	}

	private void createTab(Node action, WebDriver driver, String tab, String windowId) throws CommandException {
		Set<String> windowHandles = driver.getWindowHandles();
		if (windowHandles.contains(windowId)) {
			driver.switchTo().window(windowId);
		} else {
			try {
				createTab(driver, tab);

			} catch (NoSuchWindowException e) {
				e.printStackTrace();
				createDriver(action);
			}
		}
	}

	private String createTab(WebDriver driver, String tab) {
		((JavascriptExecutor) driver).executeScript("window.open()");

		ArrayList<String> tabs = new ArrayList<>(driver.getWindowHandles());
		String nameOrHandle = tabs.get(tabs.size() - 1);
		windowMap.put(tab, nameOrHandle);

		try {
			getDriver().switchTo().window(nameOrHandle);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return nameOrHandle;
	}

	private void createDriver(Node action) throws CommandException {
		String absolutePath = null;
		String driverType = getType(action);
		try {
			String driverPath = replaceProperties((String) getVariableValue("WEBDRIVER_PATH"));

			String driverFileName = null;
			if (SystemUtils.IS_OS_MAC) {
				driverFileName = driverType + "driver-macos";
			}
			if (SystemUtils.IS_OS_WINDOWS) {
				driverFileName = driverType + "driver.exe";
			}

			if (driverPath == null) {
				File startDir = new File(getListener().getManager().getFile(PLUGINS_DIR_NAME), driverFileName);
				if (!startDir.exists()) {
					File homePluginsDir = new File(getListener().getManager().getHomeWorkingDir(), PLUGINS_DIR_NAME);
					startDir = new File(homePluginsDir, driverFileName);
				}
				if (!startDir.exists()) {
					showDriverNotFound(driverType);
				} else {
					absolutePath = startDir.getAbsolutePath();
				}
			}

			debug("Web driver: " + absolutePath);
			String webdriverPath = "webdriver." + driverType + ".driver";
			System.setProperty(webdriverPath, absolutePath);

			WebDriver driver;
			switch (driverType) {
			case GECKO:
				FirefoxOptions profile = new FirefoxOptions();
				String binaryPath = getVariableString("FIREFOX_BINARY_PATH");
				profile.setBinary(binaryPath);
				driver = new FirefoxDriver(profile);
				break;

			case CHROME:
				ChromeOptions chromeOptions = new ChromeOptions();

				String value = action.getAttribute("deviceName");
				if (StringUtils.isNotBlank(value)) {
					Map<String, String> mobileEmulation = new HashMap<>();
					mobileEmulation.put("deviceName", value);
					chromeOptions.setExperimentalOption("mobileEmulation", mobileEmulation);
				}

				chromeOptions.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
				chromeOptions.addArguments("ignore-certificate-errors", "--start-maximized");

				driver = new ChromeDriver(chromeOptions);
				break;

			case MSEDGE:
				if ("iexplorer".equals(action.getAttribute("mode"))) {
					EdgeOptions options = new EdgeOptions();
					driver = new EdgeDriver(options);
				} else {
					InternetExplorerOptions ieOptions = new InternetExplorerOptions();
					driver = new InternetExplorerDriver(ieOptions);
				}
				break;

			default:
				String driverClass = getDriverClassName(action);
				driver = (WebDriver) Class.forName(driverClass).newInstance();
				break;
			}

			driver.manage().window().maximize();
			setDriver(driver);

		} catch (SessionNotCreatedException e) {
			if (e.getMessage().contains("only supports Chrome version")) {
				String message = org.apache.commons.lang3.StringUtils.substringBefore(e.getMessage(),
						"remote stacktrace: Backtrace:");

				if (CHROME.equals(driverType)) {
					message = message
							+ "\nTo fix this issue you should download required webdriwer from\n download page: \"https://sites.google.com/chromium.org/driver/downloads\"\n"
							+ "and unpacked to \"file:" + StringUtils.substringBeforeLast(absolutePath, "\\")
							+ "\"\nNote: webdriver file can be locked, you should close Anteater and possible kill webdriver process manually.";
				}

				throw new CommandException(new IllegalArgumentException(message, e), this, action);
			} else {
				throw new CommandException(e, this, action);
			}
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			throw new CommandException(e, this, action);
		}
	}

	private void showDriverNotFound(String driverType) throws CommandException {
		String downloadPage;
		switch (driverType) {
		case CHROME:
			downloadPage = "https://sites.google.com/chromium.org/driver/downloads";
			break;
		case GECKO:
			downloadPage = "https://github.com/mozilla/geckodriver/releases";
			break;
		case MSEDGE:
			downloadPage = "https://developer.microsoft.com/en-us/microsoft-edge/tools/webdriver";
			break;
		default:
			downloadPage = "https://www.selenium.dev/documentation/webdriver/";
		}

		File homeWorkingDir = new File(AEWorkspace.getInstance().getHomeWorkingDir(), PLUGINS_DIR_NAME);
		File baseDir = new File(AEWorkspace.getInstance().getBaseDir(), PLUGINS_DIR_NAME);
		throw new CommandException("Web driver not found.\n\n"
				+ "If you know the path to the already loaded web driver, you can use the anteater environment variable: WEBDRIVER_PATH.\n"
				+ "If you don't have the web driver, you can download it in the following folders:\n"
				+ "BaseDir: \"file:" + baseDir + "\"\n"
				+ "or HomeDir: \"file:" + homeWorkingDir + "\"\n"
				+ "\nWebdriwer download page: \"" + downloadPage + "\"\n"
				+ "After installing the web driver, an application restart is required.", this);
	}

	private String getDriverClassName(Node action) {
		return attr(action, "driver");
	}

	private String getType(Node action) {
		String defaultDriverType = getParentDriverType();
		return StringUtils.defaultIfBlank(attr(action, "type"), defaultDriverType);
	}

	private String getParentDriverType() {
		String driverType = null;
		Web parent = getWebParrentProcessor();
		if (parent != null) {
			driverType = parent.getType();
		}
		if (driverType == null) {
			driverType = StringUtils.defaultIfBlank(getVariableString(WEB_DRIVER_TYPE_VAR_NAME), DEFAULT_DRIVER_TYPE);
		}
		return driverType;
	}

	private String getType() {
		return type;
	}

	private boolean checkAuthAllert(Node action, long millisecond) {
		String username = attr(action, "username");
		boolean alertAccepted = false;
		if (StringUtils.isNotBlank(username)) {
			String password = attr(action, "password");
			for (int i = 0; i < millisecond && !isStoppedTest(); i += 100) {
				sleep(100);
				try {
					Alert alert = getDriver().switchTo().alert();
					alert.sendKeys(username + Keys.TAB + password);
					getDriver().switchTo().alert().accept();
					alertAccepted = true;
				} catch (Exception e) {
					log.debug(e);
				}
			}
		}
		return alertAccepted;
	}

	private void sleep(long millisecond) {
		try {
			Thread.sleep(millisecond);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@CommandExamples({ "<RunIfFirst>...</RunIfFirst>", "<RunIfFirst>\n\t<Else>...</Else>\t\n</RunIfFirst>" })
	public void runCommandRunIfFirst(Node action) {
		try {
			if (webParrentProcessor == null) {
				runNodes(new Node[] { action });
			} else {
				Node[] nodes = action.getNodes("Else");
				runNodes(nodes);
			}
		} catch (Exception e) {
			checkExceptionState(e);
		}

		updateTabTitle();
	}

	private void updateTabTitle() {
		try {
			WebDriver driver = getDriver();
			String windowId = driver.getWindowHandle();
			Collection<Entry<String, String>> entries = windowMap.entrySet();
			for (Entry<String, String> entry : entries) {
				if (StringUtils.equals(entry.getValue(), windowId)) {
					String title = driver.getTitle();
					String prefix = "[" + entry.getKey() + "] ";
					if (!StringUtils.startsWith(title, prefix)) {
						if (StringUtils.startsWith(title, "[")) {
							title = StringUtils.substringAfter(title, "] ");
						}
						title = prefix + title;
						((JavascriptExecutor) driver).executeScript("document.title = '" + title + "'");
					}
				}
			}
		} catch (Exception e) {
			//
		}
	}

	@CommandExamples({ "<Page url='type:string' />", "<Page url='' timeout='' username='' password=''/>" })
	public void runCommandPage(Node action) {
		String attr = attr(action, "url");
		getDriver().get(attr);
		if (checkAuthAllert(action, ALERT_TIMEOUT)) {
			getDriver().get(attr);
		}

		updateTabTitle();
	}

	@CommandExamples({ "<Text value='' name='type:string' />", "<Text value=''><name>...</name></Text>",
			"<Text value=''>" + SELECTOR_TARG_LIST + "</Text>" })
	public void runCommandText(Node action) {
		try {
			WebElement element = findElementWithTimeout(action, true);

			String text = attr(action, "value");

			if (element != null) {
				clearElement(element);
				element.sendKeys(text);

				if (StringUtils.equalsIgnoreCase("true", attr(action, "submit"))) {
					element.submit();
				}
			}

			return;

		} catch (WebDriverException e) {
			checkExceptionState(e);
		}

		updateTabTitle();
	}

	private void clearElement(WebElement element) {
		try {
			element.clear();
		} catch (Exception e) {
			//
		}
	}

	@CommandExamples({ "<CloseTab name=''/>", "<CloseTab/>" })
	public void runCommandCloseTab(Node action) {
		String tabName = attr(action, "name");
		if (tabName != null) {
			String tabId = windowMap.get(tabName);
			getDriver().switchTo().window(tabId).close();
		} else {
			Set<String> windowHandles = getDriver().getWindowHandles();
			for (String tabId : windowHandles) {
				if (!windowMap.containsValue(tabId)) {
					getDriver().switchTo().window(tabId).close();
				}
			}
		}

		updateTabTitle();
	}

	@CommandExamples({ "<Click xpath=''/>", "<Click>" + SELECTOR_TARG_LIST + "</Click>" })
	public void runCommandClick(Node action) {
		Set<String> existedWindowHandles = getDriver().getWindowHandles();

		WebElement element = findElementWithTimeout(action, true);
		if (element != null) {
			WebDriverException exception = null;
			for (long i = 0; i < getTimeout(action) && !isStoppedTest(); i += SLEEP_INTERVAL) {
				try {
					element.click();
					exception = null;
					break;
				} catch (WebDriverException e) {
					exception = e;
				}
				sleep(SLEEP_INTERVAL);
			}
			checkExceptionState(exception);
			checkAuthAllert(action, SLEEP_INTERVAL);

			String tabName = attr(action, "tab");
			if (tabName != null) {
				String newTabId = getNewTabId(existedWindowHandles);

				String tab = getCurrentTabName();
				if (newTabId == null && !tabName.equals(tab)) {
					String oldTabId = getTabName(tabName);
					if (oldTabId != null) {
						String windowHandle = getDriver().getWindowHandle();
						getDriver().switchTo().window(oldTabId).close();
						windowMap.remove(tabName);
						getDriver().switchTo().window(windowHandle);
					}
				}

				if (newTabId == null && !tabName.equals(tab)) {
					String oldTabId = windowMap.get(tab);
					windowMap.remove(tab);
					windowMap.put(tabName, oldTabId);
					createTab(getDriver(), tab);
				}

				if (newTabId != null) {
					windowMap.computeIfPresent(tabName, (k, v) -> {
						try {
							getDriver().switchTo().window(v).close();
							windowMap.remove(tabName);
						} catch (NoSuchWindowException e) {
							windowMap.remove(tabName);
						}
						return newTabId;
					});
				}
			}

			updateTabTitle();
		}
	}

	private String getTabName(String tabName) {
		String tabId = windowMap.get(tabName);

		Set<String> windowHandles = getDriver().getWindowHandles();
		for (String tab : windowHandles) {
			if (tab.equals(tabId)) {
				return tabId;
			}
		}

		return null;
	}

	private String getCurrentTabName() {
		String tabId = null;
		WebDriver driver = getDriver();
		String windowId = driver.getWindowHandle();
		Collection<Entry<String, String>> entries = windowMap.entrySet();
		for (Entry<String, String> entry : entries) {
			if (StringUtils.equals(entry.getValue(), windowId)) {
				tabId = entry.getKey();
				break;
			}
		}

		return tabId;
	}

	private String getNewTabId(Set<String> existedWindowHandles) {
		String newTabId = null;

		Set<String> windowHandles = getDriver().getWindowHandles();
		for (String tabId : windowHandles) {
			if (!windowMap.containsValue(tabId) && !existedWindowHandles.contains(tabId)) {
				newTabId = tabId;
				break;
			}
		}
		return newTabId;
	}

	@CommandExamples({ "<GetText name='result' xpath='' ></GetText>" })
	public void runCommandGetText(Node action) {
		try {
			WebElement element = findElementWithTimeout(action, true);
			String text = "";
			if (element != null) {
				String tagName = element.getTagName();
				if ("input".equals(tagName)) {
					text = element.getAttribute("value");
				} else {
					text = element.getText();
				}
				setVariableValue(attr(action, "name"), text);
			}
		} catch (Exception e) {
			checkExceptionState(e);
		}

		updateTabTitle();
	}

	@CommandExamples({ "<GetUrl name=''/>" })
	public void runCommandGetUrl(Node action) {
		try {
			String currentUrl = getDriver().getCurrentUrl();
			setVariableValue(attr(action, "name"), currentUrl);
		} catch (Exception e) {
			checkExceptionState(e);
		}

		updateTabTitle();
	}

	@Override
	@CommandExamples({ "<Frame name=''>...</Frame>", "<Frame xpath=''>...</Frame>" })
	public void runCommandFrame(Node action) throws CommandException {
		String name = attr(action, "name");
		String xpath = attr(action, "xpath");

		WebDriverException exception = null;
		boolean frameSelected = false;
		for (long i = 0; i < getTimeout(action) && !isStoppedTest(); i += SLEEP_INTERVAL) {
			try {
				if (name != null) {
					getDriver().switchTo().frame(name);

				} else if (xpath != null) {
					WebElement element = findElementWithTimeout(action, false);
					getDriver().switchTo().frame(element);
				}

				frameSelected = true;
				exception = null;
				break;
			} catch (WebDriverException e) {
				exception = e;
			}
			sleep(SLEEP_INTERVAL);
		}
		if (exception != null && !frameSelected && !isStoppedTest()) {
			checkExceptionState(exception);
		}

		try {
			taskNode(action, false);
		} finally {
			getDriver().switchTo().parentFrame();
		}

		updateTabTitle();
	}

	@CommandExamples({ "<ElementExists xpath=''>...</ElementExists>" })
	public void runCommandElementExists(Node action) throws CommandException {
		try {
			findElementWithTimeout(action, false);
			taskNode(action, false);
		} catch (Exception e) {
			Node[] nodes = action.getNodes("Else");
			runNodes(nodes);
		}

		updateTabTitle();
	}

	@CommandExamples({ "<ElementNotExists xpath=''>...</ElementNotExists>" })
	public void runCommandElementNotExists(Node action) throws CommandException {
		try {
			findElementWithTimeout(action, false);
		} catch (Exception e) {
			e = (Exception) (ExceptionUtils.getRootCause(e) != null ? ExceptionUtils.getRootCause(e) : e);
			if (e instanceof NoSuchElementException) {
				taskNode(action, false);
			} else {
				throw new CommandException(e, this, action);
			}
		}

		updateTabTitle();
	}

	@CommandExamples({ "<CheckPage url_regex='' title_regex='' timeout=''/>" })
	public void runCommandCheckPage(Node action) {
		String urlRegex = attr(action, "url_regex");
		String titleRegex = attr(action, "title_regex");

		for (long i = 0; i < getTimeout(action) && !isStoppedTest(); i += SLEEP_INTERVAL) {
			String currentUrl = getDriver().getCurrentUrl();
			String currentTitle = getDriver().getTitle();

			if (isMatched(urlRegex, currentUrl) && isMatched(titleRegex, currentTitle)) {
				updateTabTitle();
				return;
			}
			sleep(SLEEP_INTERVAL);
		}

		TestCase.fail("Page [" + (urlRegex != null ? "url_regex:" + urlRegex : " ")
				+ (titleRegex != null ? "title_regex:" + titleRegex : " ") + "] is not found.");

	}

	private boolean isMatched(String regex, String text) {
		boolean result = true;
		if (regex != null) {
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(text);
			result = matcher.matches();
		}
		return result;
	}

	private WebElement findElementWithTimeout(Node action, boolean inner) {
		WebElement findElement = null;
		Exception exception = null;

		for (long i = 0; i < getTimeout(action) && !isStoppedTest(); i += SLEEP_INTERVAL) {
			try {
				findElement = findElement(action, inner);
				exception = null;
				break;
			} catch (Exception e) {
				exception = e;
			}
			sleep(SLEEP_INTERVAL);
		}
		if (findElement == null) {
			checkExceptionState(exception);
		}

		return findElement;
	}

	private void checkExceptionState(Exception exception) {
		if (exception != null && !isStoppedTest()) {
			if (exception instanceof WebDriverException) {
				throw new InvalidArgumentException(StringUtils.substringBefore(exception.getMessage(), "\n"),
						exception);
			} else {
				throw new InvalidArgumentException(exception.getMessage(), exception);
			}
		}
	}

	private long getTimeout(Node action) {
		long result = this.timeout;
		String timeoutAttr = attr(action, "timeout");
		if (StringUtils.isNotBlank(timeoutAttr)) {
			result = Long.parseLong(timeoutAttr);
		}
		return result;
	}

	private WebElement findElement(Node action, boolean inner) throws NoSuchElementException {
		WebElement findElement = null;
		NoSuchElementException lastException = null;
		if (inner && !action.isEmpty()) {
			for (Node node : action) {
				try {
					findElement = findElementByInnerNode(node);
				} catch (NoSuchElementException e) {
					lastException = e;
				}
			}
		} else {
			try {
				Map<String, String> attributes = action.getAttributes();
				findElement = findElement(attributes);
			} catch (NoSuchElementException e) {
				lastException = e;
			}
		}
		if (findElement == null) {
			checkExceptionState(lastException);
		}
		return findElement;
	}

	private WebElement findElementByInnerNode(Node node) throws NoSuchElementException {
		String name = node.getTag();
		String value = replaceProperties(node.getInnerText());
		@SuppressWarnings("unchecked")
		Map<String, String> attributes = new LinkedMap();
		attributes.put(name, value);
		return findElement(attributes);
	}

	private WebElement findElement(Map<String, String> attributes) throws NoSuchElementException {
		WebElement element = null;
		NoSuchElementException exceptionByMName = null;
		String attr = attributes.get("name");
		attr = replaceProperties(attr);
		try {
			if (attr != null) {
				return getDriver().findElement(By.name(attr));
			}
		} catch (NoSuchElementException e) {
			exceptionByMName = e;
		}

		attr = attributes.get("className");
		if (attr != null) {
			return getDriver().findElement(By.className(attr));
		}
		attr = attributes.get("cssSelector");
		if (attr != null) {
			return getDriver().findElement(By.cssSelector(attr));
		}
		attr = attributes.get("id");
		if (attr != null) {
			return getDriver().findElement(By.id(attr));
		}
		attr = attributes.get("linkText");
		if (attr != null) {
			return getDriver().findElement(By.linkText(attr));
		}
		attr = attributes.get("partialLinkText");
		if (attr != null) {
			return getDriver().findElement(By.partialLinkText(attr));
		}
		attr = attributes.get("tagName");
		if (attr != null) {
			return getDriver().findElement(By.tagName(attr));
		}
		attr = attributes.get("xpath");
		if (attr != null) {
			return getDriver().findElement(By.xpath(attr));
		}

		if (exceptionByMName != null) {
			throw exceptionByMName;
		}
		return element;
	}

	@CommandExamples("<Title name='' />")
	public void runCommandTitle(Node action) {
		WebDriver driver = getDriver();
		if (driver != null) {
			setVariableValue(attr(action, "name"), driver.getTitle());
		} else {
			setVariableValue(attr(action, "name"), null);
		}

		updateTabTitle();
	}

	@CommandExamples("<Refresh />")
	public void runCommandRefresh(Node action) {
		WebDriver driver = getDriver();
		driver.navigate().refresh();
		updateTabTitle();
	}

	@CommandExamples({ "<CloseDriver />" })
	public void runCommandCloseDriver(Node action) {
		quiteDriver();
	}

	@SuppressWarnings("unchecked")
	@CommandExamples({ "<DeleteCookie except='type:property'/>", "<DeleteCookie />", "<DeleteCookie name=''/>" })
	public void runCommandDeleteCookie(Node action) {
		Object except = attrValue(action, "except");
		WebDriver driver = getDriver();
		Options manage = driver.manage();

		if (except != null) {
			List<String> enaledCookies = new ArrayList<>();
			if (except instanceof String) {
				enaledCookies.add((String) except);
			} else if (except instanceof List) {
				enaledCookies = (List<String>) except;
			}

			if (!enaledCookies.isEmpty()) {
				Set<Cookie> cookies = manage.getCookies();
				StringBuilder report = new StringBuilder();
				for (Cookie cookie : cookies) {
					String name = cookie.getName();
					String value = cookie.getValue();
					if (!enaledCookies.contains(name)) {
						manage.deleteCookieNamed(name);
						report.append("\n" + name + "=" + value);
					}
				}
				if (report.length() > 0) {
					debug("Removed cookies: " + report);
				}
			}
		} else {
			String cookieName = attr(action, "name");

			if (StringUtils.isBlank(cookieName)) {
				Set<Cookie> cookies = manage.getCookies();
				if (!cookies.isEmpty()) {
					StringBuilder report = new StringBuilder("Removed cookies: ");
					for (Cookie cookie : cookies) {
						report.append("\n" + cookie.getName() + "=" + cookie.getValue());
					}
					debug(report);
				}

				getDriver().manage().deleteAllCookies();

			} else {
				getDriver().manage().deleteCookieNamed(cookieName);
			}

		}

		updateTabTitle();
	}

	@SuppressWarnings("unchecked")
	@CommandExamples({ "<CookieReport name='type:property'/>" })
	public void runCommandCookieReport(Node action) {
		String name = attr(action, "name");
		Map<String, String> report = (Map<String, String>) getVariableValue(name);
		if (report == null) {
			report = new LinkedMap();
		}
		WebDriver driver = getDriver();
		Options manage = driver.manage();
		Set<Cookie> cookies = manage.getCookies();
		for (Cookie cookie : cookies) {
			report.put(cookie.getName(), cookie.getValue());
		}
		setVariableValue(name, report);

		updateTabTitle();
	}

	@Override
	public void complete(boolean success) {
		updateTabTitle();
	}

	private void quiteDriver() {
		if (getDriver() != null) {
			try {
				getDriver().quit();
			} catch (Exception e) {
				//
			}
			setDriver(null);
		}
	}

	private WebDriver getDriver() {
		return WebDriverManager.getDriver(driverName);
	}

	private WebDriver setDriver(WebDriver webDriver) {
		return WebDriverManager.setDriver(driverName, webDriver);
	}

}
