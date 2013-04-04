package de.siemens.quantarch.bugs;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import b4j.core.DefaultSearchData;
import b4j.core.Issue;
import b4j.core.SearchResultCountCallback;
import b4j.core.session.HttpBugzillaSession;
import de.siemens.quantarch.bugs.dao.QuantArchBugzillaDAO;
import de.siemens.quantarch.bugs.history.BugHistory;
import de.siemens.quantarch.bugs.history.FetchBugzillaHistroy;
import de.siemens.quantarch.bugs.history.FetchHistory;
import de.siemens.quantarch.bugs.products.ParseProducts;

public class ParseBugzilla implements SearchResultCountCallback {

	private static Logger log = Logger.getLogger(ParseBugzilla.class);

	public void parseBugs(List<String> possibleBugStatusList, long projectId)
			throws ConfigurationException, UnsupportedEncodingException {

		ApplicationContext context = new ClassPathXmlApplicationContext(
				"beans.xml");
		QuantArchBugzillaDAO bugzillaDAO = (QuantArchBugzillaDAO) context
				.getBean("bugzillaDAO");
		FetchHistory historyFetcher = new FetchBugzillaHistroy(
				"https://bugzilla.kernel.org");
		ParseProducts prodParser = new ParseProducts(
				"https://bugzilla.kernel.org");

		// Add products into the database
		List<String> products = prodParser.fetchProducts();
		for (String product : products) {
			bugzillaDAO.addProduct(product, projectId);
		}

		// Configure from file
		File file = new File("myConfig.xml");
		if (file.exists()) {
			XMLConfiguration myConfig = new XMLConfiguration(file);

			// Create the session
			HttpBugzillaSession session = new HttpBugzillaSession();
			session.configure(myConfig);

			// Open the session
			if (session.open()) {
				// Search a bug

				for (String product : products) {
					for (String status : possibleBugStatusList) {
						DefaultSearchData searchData = new DefaultSearchData();
						searchData.add("bug_status", status);
						searchData.add("product",
								URLEncoder.encode(product, "UTF-8"));
						// Perform the search
						Iterator<Issue> i = session
								.searchBugs(searchData, this);
						while (i.hasNext()) {
							Issue issue = i.next();
							List<BugHistory> bugHistoryList = historyFetcher
									.fetchBugHistory(issue.getId());
							bugzillaDAO.addIssue(issue, projectId,
									bugHistoryList);
						}
					}
				}
				session.close();
			}
		} else {
			System.out.println("File does not exist");
		}
	}

	public static void main(String[] args) throws ConfigurationException,
			UnsupportedEncodingException {
		ParseBugzilla b = new ParseBugzilla();
		List<String> possibleStatuses = new ArrayList<String>();
		possibleStatuses.add("NEW");
		possibleStatuses.add("ASSIGNED");
		possibleStatuses.add("REOPENED");
		possibleStatuses.add("RESOLVED");
		possibleStatuses.add("VERIFIED");
		possibleStatuses.add("REJECTED");
		possibleStatuses.add("DEFERRED");
		possibleStatuses.add("NEEDINFO");
		possibleStatuses.add("CLOSED");
		b.parseBugs(possibleStatuses, 1L);
	}

	@Override
	public void setResultCount(int resultCount) {
		log.info("Number of bugs:" + resultCount);
	}
}
