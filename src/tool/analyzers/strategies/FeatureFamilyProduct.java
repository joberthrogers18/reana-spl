package tool.analyzers.strategies;

import java.util.Collection;
import java.util.stream.Stream;

import tool.CyclicRdgException;
import tool.RDGNode;
import tool.UnknownFeatureException;
import tool.analyzers.IReliabilityAnalysisResults;
import tool.analyzers.buildingblocks.ConcurrencyStrategy;

public class FeatureFamilyProduct {

	public IReliabilityAnalysisResults evaluateReliability(RDGNode node) throws CyclicRdgException, UnknownFeatureException {
		
		System.out.println(node);
		
		return null;
	}
}
