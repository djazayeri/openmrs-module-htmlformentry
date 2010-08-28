package org.openmrs.module.htmlformentry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.junit.Assert;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext.Mode;
import org.openmrs.util.Format;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.mock.web.MockHttpServletRequest;

public abstract class RegressionTestHelper {
	
	/**
	 * @return will be used to look up the file test/.../include/{formName}.xml   
	 */
	abstract String getFormName();
	
	/**
	 * you probably want to override this
	 * @return the labels before all widgets you want to set values for
	 * (allows you to refer to "Date:" instead of "w1" in setupRequest) 
	 */
	String[] widgetLabels() {
		return new String[0];
	}

	/**
	 * (Override this if you want to test the submission of a form.)
	 * Set any request parameters that will be sent in the form submission
	 * @param request an empty request for you to populate
	 * @param widgets map from the label you provided in widgetLabels() to the name that form element should submit as (which was autogenerated by the html form, e.g. "w3") 
	 */
	void setupRequest(MockHttpServletRequest request, Map<String, String> widgets) {
	}
	
	/**
	 * (Override this if you want to test the submission of a form.)
	 * @param results the results of having submitted the request you set up in setupRequest. This will contain either validationErrors, or else an encounterCreated  
	 */
	void testResults(SubmissionResults results) {
	}
	
	/**
	 * Optionally override this if you want to generate the form for a different patient
	 * @return
	 */
	Patient getPatient() {
		return Context.getPatientService().getPatient(2);	
	}
	
	/**
	 * Optionally override this if you want to test out viewing a specific encounter rather
	 * than the one that was created earlier in this test case.
	 * @return
	 * @throws Exception 
	 */
	Encounter getEncounterToView() throws Exception {
		return null;
	}
	
	/**
	 * Override this and return true if you want to have testViewingEncounter run. (If you override
	 * getEncounterToView to return something non-null, then you do not need to override this method --
	 * testViewingEncounter will be called anyway.)
	 */
	boolean doViewEncounter() {
	    return false;
    }

	/**
	 * Override this if you want to test out viewing 
	 * @param encounter 
	 * @param html
	 */
	void testViewingEncounter(Encounter encounter, String html) {
	}
	
	public void run() throws Exception {
		Patient patient = getPatient();
		FormEntrySession session = setupFormEntrySession(patient, getFormName());
		String html = session.getHtmlToDisplay();
		//System.out.println(html);
		
		Map<String, String> labeledWidgets = getLabeledWidgets(html, widgetLabels());
		MockHttpServletRequest request = new MockHttpServletRequest();
		setupRequest(request, labeledWidgets);
		Encounter toView = null;
		if (request.getParameterMap().size() > 0) {
			SubmissionResults results = doSubmission(session, request);
			testResults(results);
			toView = results.getEncounterCreated();
		}
		
		Encounter override = getEncounterToView();
		boolean doViewEncounter = override != null || doViewEncounter();
		if (!doViewEncounter)
			return;
		
		if (override != null)
			toView = override;
		session = setupFormViewSession(patient, toView, getFormName());
		html = session.getHtmlToDisplay();
		testViewingEncounter(toView, html);
	}
	
	private FormEntrySession setupFormEntrySession(Patient patient, String filename) throws Exception {
		String xml = loadXmlFromFile(RegressionTests.XML_DATASET_PATH + filename + ".xml");
		
		HtmlForm fakeForm = new HtmlForm();
        fakeForm.setXmlData(xml);
        fakeForm.setForm(new Form(1));
        FormEntrySession session = new FormEntrySession(patient, null, FormEntryContext.Mode.ENTER, fakeForm);
        return session;
    }
	
	private FormEntrySession setupFormViewSession(Patient patient, Encounter encounter, String filename) throws Exception {
		String xml = loadXmlFromFile(RegressionTests.XML_DATASET_PATH + filename + ".xml");
		
		HtmlForm fakeForm = new HtmlForm();
        fakeForm.setXmlData(xml);
        fakeForm.setForm(new Form(1));
        FormEntrySession session = new FormEntrySession(patient, encounter, FormEntryContext.Mode.VIEW, fakeForm);
        return session;
    }
	
	private String loadXmlFromFile(String filename) throws Exception {
		InputStream fileInInputStreamFormat = null;
		
		// try to load the file if its a straight up path to the file or
		// if its a classpath path to the file
		if (new File(filename).exists()) {
			fileInInputStreamFormat = new FileInputStream(filename);
		} else {
			fileInInputStreamFormat = getClass().getClassLoader().getResourceAsStream(filename);
			if (fileInInputStreamFormat == null)
				throw new FileNotFoundException("Unable to find '" + filename + "' in the classpath");
		}
		StringBuilder sb = new StringBuilder();
		BufferedReader r = new BufferedReader(new InputStreamReader(fileInInputStreamFormat, Charset.forName("UTF-8")));
		while (true) {
			String line = r.readLine();
			if (line == null)
				break;
			sb.append(line).append("\n");
		}
		return sb.toString();
    }
	
	String dateAsString(Date date) {
		return Context.getDateFormat().format(date);
	}

	String dateTodayAsString() {
	    return dateAsString(new Date());
    }

	/**
	 * Finds the name of the first widget after each of the given labels. I.e. the first name="w#".
	 */
	private Map<String, String> getLabeledWidgets(String html, String... labels) {
		Map<String, String> ret = new HashMap<String, String>();
		for (String label : labels) {
			int index = html.indexOf(label);
			if (index < 0)
				continue;
			try {
				index = html.indexOf("name=\"w", index);
				index = html.indexOf('"', index) + 1;
				String val = html.substring(index, html.indexOf('"', index + 1));
				ret.put(label, val);
			} catch (Exception ex) {
				// do nothing
			}
		}
		return ret;
    }

	private SubmissionResults doSubmission(FormEntrySession session, HttpServletRequest request) throws Exception {
		SubmissionResults results = new SubmissionResults();
		session.prepareForSubmit();
		List<FormSubmissionError> validationErrors = session.getSubmissionController().validateSubmission(session.getContext(), request);
		if (validationErrors != null && validationErrors.size() > 0) {
			results.setValidationErrors(validationErrors);
			return results;
		}
        session.getSubmissionController().handleFormSubmission(session, request);
        if (session.getContext().getMode() == Mode.ENTER && (session.getSubmissionActions().getEncountersToCreate() == null || session.getSubmissionActions().getEncountersToCreate().size() == 0))
            throw new IllegalArgumentException("This form is not going to create an encounter");
        session.applyActions();
        results.setEncounterCreated(getLastEncounter(session.getPatient()));
        return results;
    }

	private Encounter getLastEncounter(Patient patient) {
	    List<Encounter> encs = Context.getEncounterService().getEncounters(patient, null, null, null, null, null, null, false);
	    if (encs == null || encs.size() == 0)
	    	return null;
	    if (encs.size() == 1)
	    	return encs.get(0);
	    Collections.sort(encs, new Comparator<Encounter>() {
			@Override
            public int compare(Encounter left, Encounter right) {
	            return OpenmrsUtil.compareWithNullAsEarliest(left.getEncounterDatetime(), right.getEncounterDatetime());
            }
	    });
	    return encs.get(encs.size() - 1);
    }

	class SubmissionResults {
		private List<FormSubmissionError> validationErrors; 
		private Encounter encounterCreated;

		public void assertNoEncounterCreated() {
			Assert.assertNull(encounterCreated);
        }
		
		public void assertEncounterCreated() {
			Assert.assertNotNull(encounterCreated);
        }
		
		public void assertNoErrors() {
			Assert.assertTrue(validationErrors == null || validationErrors.size() == 0);
		}
		
		public void assertErrors() {
			Assert.assertTrue(validationErrors != null && validationErrors.size() > 0);
		}
		
		public void assertErrors(int numberOfErrors) {
			Assert.assertTrue(validationErrors != null && validationErrors.size() == numberOfErrors);
		}
		
		public void printErrors() {
	        if (validationErrors == null || validationErrors .size() == 0) {
	        	System.out.println("No Errors");
	        } else {
	        	for (FormSubmissionError error : validationErrors)
	        		System.out.println(error.getId() + " -> " + error.getError());
	        }
        }
		
		public void print() {
            printErrors();
            printEncounterCreated();
        }

		
        public void printEncounterCreated() {
	        if (encounterCreated == null) {
	        	System.out.println("No encounter created");
	        } else {
	        	System.out.println("=== Encounter created ===");
	        	System.out.println("Date: " + encounterCreated.getEncounterDatetime());
	        	System.out.println("Location: " + encounterCreated.getLocation().getName());
	        	System.out.println("Provider: " + encounterCreated.getProvider().getPersonName());
	        	System.out.println("    (obs)");
	        	Collection<Obs> obs = encounterCreated.getAllObs(false);
	        	if (obs == null) {
	        		System.out.println("None");
	        	} else {
	        		for (Obs o : obs) {
	        			System.out.println(o.getConcept().getName() + " -> " + o.getValueAsString(Context.getLocale()));
	        		}
	        	}
	        }
        }

		public List<FormSubmissionError> getValidationErrors() {
        	return validationErrors;
        }
        public void setValidationErrors(List<FormSubmissionError> validationErrors) {
        	this.validationErrors = validationErrors;
        }
        public Encounter getEncounterCreated() {
        	return encounterCreated;
        }
        public void setEncounterCreated(Encounter encounterCreated) {
        	this.encounterCreated = encounterCreated;
        }

        /**
         * Fails if the number of obs in encounterCreated is not 'expected'
         * @param expected
         */
		public void assertObsCreatedCount(int expected) {
			int found = getObsCreatedCount();
	        Assert.assertEquals("Expected to create " + expected + " obs but got " + found, expected, found);
        }
		
		/**
		 * Fails if the number of obs groups in encounterCreated is not 'expected'
		 * @param expected
		 */
		public void assertObsGroupCreatedCount(int expected) {
	        int found = getObsGroupCreatedCount();
	        Assert.assertEquals("Expected to create " + expected + " obs groups but got " + found, expected, found);
        }
		
		/**
         * Fails if the number of obs leaves (i.e. obs that aren't groups) in encounterCreated is not 'expected'
         * @param expected
         */
		public void assertObsLeafCreatedCount(int expected) {
			int found = getObsLeafCreatedCount();
	        Assert.assertEquals("Expected to create " + expected + " non-group obs but got " + found, expected, found);
        }

		/**
		 * @return the number of obs in encounterCreated (0 if no encounter was created)
		 */
		public int getObsCreatedCount() {
	        if (encounterCreated == null)
	        	return 0;
	        Collection<Obs> temp = encounterCreated.getAllObs();
	        if (temp == null)
	        	return 0;
	        return temp.size();
        }

		/**
		 * @return the number of obs groups in encounterCreated (0 if no encounter was created)
		 */
		public int getObsGroupCreatedCount() {
	        if (encounterCreated == null)
	        	return 0;
	        Collection<Obs> temp = encounterCreated.getAllObs();
	        if (temp == null)
	        	return 0;
	        int count = 0;
	        for (Obs o : temp) {
	        	if (o.isObsGrouping())
	        		++count;
	        }
	        return count;
        }

		/**
		 * @return the number of non-group obs in encounterCreated (0 if no encounter was created)
		 */
		public int getObsLeafCreatedCount() {
	        if (encounterCreated == null)
	        	return 0;
	        Collection<Obs> temp = encounterCreated.getObs();
	        if (temp == null)
	        	return 0;
	        return temp.size();
        }

		/**
		 * Fails if encounterCreated doesn't have an obs with the given conceptId and value
		 * @param conceptId
		 * @param value may be null
		 */
		public void assertObsCreated(int conceptId, Object value) {
			// quick checks
	        Assert.assertNotNull(encounterCreated);
	        Collection<Obs> temp = encounterCreated.getAllObs();
	        Assert.assertNotNull(temp);

	        String valueAsString = valueAsStringHelper(value);
	        for (Obs obs : temp) {
	        	if (obs.getConcept().getConceptId() == conceptId) {
	        		if (valueAsString == null)
	        			return;
	        		if (valueAsString.equals(obs.getValueAsString(Context.getLocale())))
	        			return;
	        	}
	        }
	        Assert.fail("Could not find obs with conceptId " + conceptId + " and value " + valueAsString);
        }
		
		/**
		 * Fails if there isn't an obs group with these exact characteristics
		 * 
		 * @param groupingConceptId the concept id of the grouping obs
		 * @param conceptIdsAndValues these parameters must be given in pairs, the first element of
		 *            which is the conceptId of a child obs (Integer) and the second element of
		 *            which is the value of the child obs
		 */
		public void assertObsGroupCreated(int groupingConceptId, Object... conceptIdsAndValues) {
			// quick checks
	        Assert.assertNotNull(encounterCreated);
	        Collection<Obs> temp = encounterCreated.getAllObs();
	        Assert.assertNotNull(temp);
	        
	        List<ObsValue> expected = new ArrayList<ObsValue>();
	        for (int i = 0; i < conceptIdsAndValues.length; i += 2) {
	        	int conceptId = (Integer) conceptIdsAndValues[i];
	        	Object value = conceptIdsAndValues[i + 1];
	        	expected.add(new ObsValue(conceptId, value));
	        }
	        
	        for (Obs o : temp) {
	        	if (o.getConcept().getConceptId() == groupingConceptId) {
	        		if (o.getValueCoded() != null || o.getValueComplex() != null || o.getValueDatetime() != null || o.getValueDrug() != null || o.getValueNumeric() != null || o.getValueText() != null) {
	        			Assert.fail("Obs group with groupingConceptId " + groupingConceptId + " should has a non-null value");
	        		}
	        		if (isMatchingObsGroup(o, expected)) {
	        			return;
	        		}
	        	}
	        }
	        Assert.fail("Cannot find an obs group matching " + expected);
        }
		
	}

	class ObsValue {
		public Integer conceptId; // required
		public Object value; // can be null
		public ObsValue(Integer cId, Object val) {
			conceptId = cId;
			value = val;
		}
		public String toString() {
			return conceptId + "->" + value;
		}
		public boolean matches(Obs obs) {
			if (obs.getConcept().getConceptId() != conceptId)
				return false;
			return OpenmrsUtil.nullSafeEquals(valueAsStringHelper(value), obs.getValueAsString(Context.getLocale()));
        }
	}

	/**
	 * Tests whether the child obs of this group exactly match 'expected'
	 * @param group
	 * @param expected
	 * @return
	 */
	public boolean isMatchingObsGroup(Obs group, List<ObsValue> expected) {
		if (!group.isObsGrouping())
			return false;
		
		Set<Obs> children = group.getGroupMembers();
		if (children.size() != expected.size())
			return false;
		
		boolean[] alreadyUsed = new boolean[expected.size()];
		for (Obs child : children) {
			boolean foundMatch = false;
			for (int i = 0; i < expected.size(); ++i) {
				if (alreadyUsed[i])
					continue;
				if (expected.get(i).matches(child)) {
					foundMatch = true;
					alreadyUsed[i] = true;
					break;
				}
			}
			if (!foundMatch)
				return false;
		}
		return true;
    }

	public String valueAsStringHelper(Object value) {
		if (value == null)
			return null;
		if (value instanceof Concept)
			return ((Concept) value).getName(Context.getLocale()).getName();
		else if (value instanceof Date)
			return Format.format((Date) value);
		else if (value instanceof Number)
			return "" + ((Number) value).doubleValue();
		else
			return value.toString();
    }
	
	/**
	 * Ignores white space.
	 * Ignores capitalization.
	 * Strips <span class="value">...</span>.
	 * Removes <span class="emptyValue">___</span>.
	 * Strips <htmlform>...</htmlform>.
	 */
	public void assertFuzzyEquals(String expected, String actual) {
		if (expected == null && actual == null)
			return;
		if (expected == null || actual == null)
			Assert.fail(expected + " does not match " + actual);
	    String test1 = fuzzyEqualsHelper(expected);
	    String test2 = fuzzyEqualsHelper(actual);
	    if (!test1.equals(test2)) {
	    	Assert.fail(expected + " does not match " + actual);
	    	//Assert.fail(test1 + " VERSUS " + test2);
	    }
    }

	private String fuzzyEqualsHelper(String string) {
		string = string.toLowerCase();
		string = string.replaceAll("<span class=\"value\">(.*)</span>", "$1");
		string = string.replaceAll("<span class=\"emptyvalue\">.*</span>", "");
		string = string.replaceAll("\\s", "");
		string = string.replaceAll("<htmlform>(.*)</htmlform>", "$1");
		return string;
    }
	

	public void addObs(Encounter encounter, Integer conceptId, Object value, Date date) {
		Person person = encounter.getPatient();
		Concept concept = Context.getConceptService().getConcept(conceptId);
		Location location = encounter.getLocation();
		Obs obs = new Obs(person, concept, date, location);
		if (value != null) {
			if (value instanceof Number)
				obs.setValueNumeric(((Number) value).doubleValue());
			else if (value instanceof String)
				obs.setValueText((String) value);
			else if (value instanceof Date)
				obs.setValueDatetime((Date) value);
			else if (value instanceof Concept)
				obs.setValueCoded((Concept) value);
		}
		obs.setDateCreated(new Date());
		encounter.addObs(obs);
    }

}
