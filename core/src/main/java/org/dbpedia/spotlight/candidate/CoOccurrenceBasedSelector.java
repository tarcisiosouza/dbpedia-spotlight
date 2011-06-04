package org.dbpedia.spotlight.candidate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dbpedia.spotlight.candidate.cooccurrence.ClassifierFactory;
import org.dbpedia.spotlight.candidate.cooccurrence.classification.CandidateClass;
import org.dbpedia.spotlight.candidate.cooccurrence.classification.CandidateClassification;
import org.dbpedia.spotlight.candidate.cooccurrence.classification.CandidateClassifier;
import org.dbpedia.spotlight.candidate.cooccurrence.features.data.OccurrenceDataProviderSQL;
import org.dbpedia.spotlight.candidate.cooccurrence.filter.FilterPOS;
import org.dbpedia.spotlight.candidate.cooccurrence.filter.FilterPattern;
import org.dbpedia.spotlight.candidate.cooccurrence.filter.FilterTermsize;
import org.dbpedia.spotlight.exceptions.InitializationException;
import org.dbpedia.spotlight.model.SpotterConfiguration;
import org.dbpedia.spotlight.model.SurfaceFormOccurrence;
import org.dbpedia.spotlight.model.TaggedText;
import org.dbpedia.spotlight.spot.TaggedSpotSelector;
import org.dbpedia.spotlight.tagging.TaggedToken;

import java.util.LinkedList;
import java.util.List;

/**
 * Candidate selector based on co-occurrence data and two classifiers for unigram
 * and ngram candidates.
 *
 * @author Joachim Daiber
 */
public class CoOccurrenceBasedSelector implements TaggedSpotSelector {

	private final Log LOG = LogFactory.getLog(this.getClass());

	public CoOccurrenceBasedSelector(SpotterConfiguration configuration) throws InitializationException {

		//TODO use its own config instead of general one.
		
		LOG.info("Initializing occurrence data provider.");
		OccurrenceDataProviderSQL.initialize(configuration);
		LOG.info("Done.");

		LOG.info("Initializing candidate classifiers.");
		new ClassifierFactory(configuration.getCandidateClassifierUnigram(),
				configuration.getCandidateClassifierNGram(),
				OccurrenceDataProviderSQL.getInstance()
				);
		
		LOG.info("Done.");
		
	}

	public List<SurfaceFormOccurrence> select(List<SurfaceFormOccurrence> surfaceFormOccurrences) {

		LinkedList<SurfaceFormOccurrence> selectedOccurrences = new LinkedList<SurfaceFormOccurrence>();

		FilterPOS filterPOS = new FilterPOS();
		FilterTermsize unigramFilter = new FilterTermsize(FilterTermsize.Termsize.unigram);
		FilterPattern filterPattern = new FilterPattern();

		CandidateClassifier unigramClassifier = ClassifierFactory.getClassifierUnigram();
		CandidateClassifier ngramClassifier = ClassifierFactory.getClassifierNGram();

		assert unigramClassifier != null;
		assert ngramClassifier != null;

		//ngramClassifier.setVerboseMode(true);
		//unigramClassifier.setVerboseMode(true);

		for(SurfaceFormOccurrence surfaceFormOccurrence : surfaceFormOccurrences) {

            if (! (surfaceFormOccurrence.context() instanceof TaggedText)) { //FIXME added this to avoid breaking, but code below will never run if we don't pass the taggedtext
                LOG.warn("SurfaceFormOccurrence did not contain TaggedText. Cannot apply "+this.getClass());
				
                selectedOccurrences.add(surfaceFormOccurrence);
                continue;
            }

			if(unigramFilter.applies(surfaceFormOccurrence)) {

				/**
				 * Unigram (n = 1)
				 */

				if(!filterPOS.applies(surfaceFormOccurrence)) {

					/**
					 * The Surface Form is on the POS blacklist, i.e. a single adjective,
					 * verb, etc.
					 */

					//TODO: add if high TF-IDF/PMI or if uppercase Adj?


					if(Character.isUpperCase(surfaceFormOccurrence.surfaceForm().name().charAt(0))){
						TaggedToken taggedToken = ((TaggedText) surfaceFormOccurrence.context()).taggedTokenProvider().getTaggedTokens(surfaceFormOccurrence).get(0);

						/**
						 * Add uppercase adjectives (e.g. Canadian tv star)
						 */
						if(taggedToken.getPOSTag() != null && taggedToken.getPOSTag().startsWith("j"))
							selectedOccurrences.add(surfaceFormOccurrence);

					}else{

						LOG.info("Dropped by POS filter: " + surfaceFormOccurrence);

					}

				}else if(!filterPattern.applies(surfaceFormOccurrence)){
					LOG.info("Dropped by Pattern filter: " + surfaceFormOccurrence);
				}else{

					CandidateClassification candidateClassification = null;
					try {
						candidateClassification = unigramClassifier.classify(surfaceFormOccurrence);
					} catch (Exception e) {
						LOG.error("Exception when classyfing unigram candidate: " + e);
						continue;
					}

					if(candidateClassification.getCandidateClass() == CandidateClass.term) {
						selectedOccurrences.add(surfaceFormOccurrence);
						//LOG.info(("Kept by UnigramClassifier (Confidence: " + candidateClassification.getConfidence() + "): " + surfaceFormOccurrence);
					}else{
						LOG.info("Dropped by UnigramClassifier (Confidence: " + candidateClassification.getConfidence() + "): " + surfaceFormOccurrence);
					}

				}


			}else{

				/**
				 * n > 1
				 */

				CandidateClassification candidateClassification = null;
				try{
					candidateClassification = ngramClassifier.classify(surfaceFormOccurrence);
				}catch (Exception e) {
					LOG.error("Exception when classyfing ngram candidate: " + e);
					continue;
				}

				if(candidateClassification.getCandidateClass() == CandidateClass.term) {
					selectedOccurrences.add(surfaceFormOccurrence);
					//LOG.info("Kept by nGramClassifier (Confidence: " + candidateClassification.getConfidence() + "): " + surfaceFormOccurrence);
				}else{
					LOG.info("Dropped by NGramClassifier: " + surfaceFormOccurrence);
				}

			}

		}
		
		return selectedOccurrences;
	}

}