package com.ganteater.ae.processor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage.RecipientType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.helper.SmarterMessageListener;
import org.subethamail.smtp.helper.SmarterMessageListener.Receiver;
import org.subethamail.smtp.helper.SmarterMessageListenerAdapter;
import org.subethamail.smtp.server.SMTPServer;

import com.ganteater.ae.CommandException;
import com.ganteater.ae.processor.annotation.CommandExamples;
import com.ganteater.ae.util.xml.easyparser.Node;
import com.sun.mail.smtp.SMTPMessage;

public class Mail extends TaskProcessor {

	private AeSMTPServer server;

	public Mail(TaskProcessor aParent) {
		super(aParent);
	}

	class AEReceiver implements Receiver {

		private Node action;
		private String to;
		private String from;

		public AEReceiver(Node action, String from, String to) {
			this.action = action;
			this.to = to;
			this.from = from;
		}

		@Override
		public void deliver(InputStream paramInputStream) throws IOException {

			String body = attr(action, "body");
			setVariableValue(body, IOUtils.toByteArray(paramInputStream));

			log.debug("Received email from: [" + from + "] to: [" + to + "]");
			String toName = attr(action, "to");
			if (toName != null) {
				setVariableValue(toName, to);
			}
			String fromName = attr(action, "from");
			if (fromName != null) {
				setVariableValue(fromName, from);
			}
			try {
				taskNode(action, false);
			} catch (CommandException e) {
				server.stop(e);
			}
		}

		@Override
		@SuppressWarnings("squid:S1186")
		public void done() {

		}

	}

	class AESmarterMessageListener implements SmarterMessageListener {

		private Node action;

		public AESmarterMessageListener(Node action) {
			this.action = action;
		}

		@Override
		public Receiver accept(String from, String to) {
			String fromFilter = action.getAttribute("from-filter");
			boolean matchesFrom = Pattern.matches(StringUtils.defaultString(fromFilter, ".*"), from);

			String toFilter = action.getAttribute("to-filter");
			boolean matchesTo = Pattern.matches(StringUtils.defaultString(toFilter, ".*"), to);
			if (matchesFrom && matchesTo) {
				return new AEReceiver(action, from, to);
			}

			return null;
		}
	}

	class AeSMTPServer extends SMTPServer {

		private CommandException exception;

		public AeSMTPServer(MessageHandlerFactory handlerFactory) {
			super(handlerFactory);
		}

		public void stop(CommandException e) {
			this.exception = e;
			super.stop();
		}

		public CommandException getException() {
			return exception;
		}
	}

	@CommandExamples({ "<SMTPServer port='type:integer'>\n"
			+ "<deliver body='type:property' from='type:property' to='type:property'>\n" + "+"
			+ "<Out name='from'/>\n<Out name='to'/>\n</deliver>\n<Out name='body' />\n" + "</SMTPServer>" })
	@SuppressWarnings("squid:S2142")
	public void runCommandSMTPServer(final Node command) throws CommandException {

		Collection<SmarterMessageListener> listeners = new ArrayList<>();

		Node[] delivers = command.getNodes("deliver");
		for (Node deliver : delivers) {
			listeners.add(new AESmarterMessageListener(deliver));
		}

		server = new AeSMTPServer(new SmarterMessageListenerAdapter(listeners));

		final String port = attr(command, "port");
		if (port != null) {
			server.setPort(Integer.parseInt(port));
		}

		server.start();

		while (server.isRunning())
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new CommandException(e, this, command);
			}

		CommandException exception = server.getException();
		if (exception != null) {
			throw exception;
		}
	}

	@SuppressWarnings({ "squid:S2068", "squid:MethodCyclomaticComplexity" })
	@CommandExamples({
			"<Send username='type:string' password='type:string' mimeType='text/plain' body='type:property' host='type:string' port='type:string'>\n\t<recipient type='enum:TO|CC|BCC' address='type:string'/>\n\t</Send>" })
	public void runCommandSend(final Node command) throws CommandException {

		final String ssl = attr(command, "ssl");
		String host = attr(command, "host");
		String port = attr(command, "port");
		String mimeType = attr(command, "mimeType");

		final String username = attr(command, "username");
		final String password = attr(command, "password");

		Session session;
		if (StringUtils.equalsIgnoreCase(ssl, "true")) {
			Properties props = new Properties();
			props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			props.put("mail.smtp.auth", "true");

			if (host != null) {
				props.setProperty("mail.host", host);
			}

			if (port != null) {
				props.setProperty("mail.smtp.port", port);
				props.put("mail.smtp.socketFactory.port", port);
			}

			session = Session.getInstance(props, new javax.mail.Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username, password);
				}
			});

		} else {
			Properties props = new Properties();

			if (host != null) {
				props.setProperty("mail.host", host);
			}

			if (port != null) {
				props.setProperty("mail.smtp.port", port);
			}

			props.setProperty("mail.transport.protocol", "smtp");
			props.setProperty("mail.debug", "true");

			if (username == null) {
				session = Session.getInstance(props);
			} else {
				props.setProperty("mail.smtp.auth", "true");
				session = Session.getInstance(props, new javax.mail.Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(username, password);
					}
				});
			}
		}

		try {

			Object bodyObj = getVariableValue(attr(command, "body"));
			Message message;
			if (StringUtils.isBlank(mimeType) && bodyObj instanceof byte[]) {
				ByteArrayInputStream bis = new ByteArrayInputStream((byte[]) bodyObj);
				message = new SMTPMessage(session, bis);
			} else {
				message = new SMTPMessage(session);
				message.setContent(bodyObj, StringUtils.defaultIfBlank(mimeType, "text/plain"));
			}

			final Node[] recipients = command.getNodes("recipient");
			for (Node recipient : recipients) {

				String rtype = attr(recipient, "type", "TO");

				javax.mail.Message.RecipientType type;
				switch (StringUtils.upperCase(rtype)) {
				case "CC":
					type = RecipientType.CC;
					break;
				case "BCC":
					type = RecipientType.BCC;
					break;
				default:
					type = RecipientType.TO;
				}

				String address = attr(recipient, "address");
				if (StringUtils.isNotBlank(address)) {
					InternetAddress iaddress = new InternetAddress(address);
					message.addRecipient(type, iaddress);
				}
			}

			Transport.send(message);
			debug("Email sent to: " + ArrayUtils.toString(message.getAllRecipients()));

		} catch (MessagingException e) {
			throw new CommandException(e, this, command);
		}

	}

	@Override
	public void stop() {
		if (server != null)
			server.stop();
		super.stop();
	}

}
