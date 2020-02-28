package tool.analyzers.strategies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import expressionsolver.Expression;
import expressionsolver.ExpressionSolver;
import fdtmc.FDTMC;
import fdtmc.State;
import jadd.ADD;
import jadd.JADD;
import paramwrapper.ParametricModelChecker;
import tool.CyclicRdgException;
import tool.RDGNode;
import tool.UnknownFeatureException;
import tool.analyzers.IReliabilityAnalysisResults;
import tool.analyzers.buildingblocks.Component;
import tool.analyzers.buildingblocks.ConcurrencyStrategy;
import tool.analyzers.buildingblocks.DerivationFunction;
import tool.analyzers.buildingblocks.FamilyBasedHelper;
import tool.analyzers.buildingblocks.IfOperator;
import tool.analyzers.buildingblocks.PresenceConditions;
import tool.analyzers.buildingblocks.ProductIterationHelper;
import tool.stats.IFormulaCollector;
import tool.stats.ITimeCollector;

public class FeatureFamilyProduct {
	
	private ExpressionSolver expressionSolver;
	private ADD featureModel;
	private ParametricModelChecker modelChecker;
	private ITimeCollector timeCollector;
	private IFormulaCollector formulaCollector;
	private FeatureBasedFirstPhase firstPhase;
	private FamilyBasedHelper helper;
	private DerivationFunction<Boolean, String, Double> solve;
	private DerivationFunction<Boolean, FDTMC, FDTMC> derive;

	public FeatureFamilyProduct (
			JADD jadd,
            ADD featureModel,
            ParametricModelChecker modelChecker,
            ITimeCollector timeCollector,
            IFormulaCollector formulaCollector		
	) {
		 	this.expressionSolver = new ExpressionSolver(jadd);
	        this.featureModel = featureModel;
	        this.modelChecker = modelChecker;

	        this.timeCollector = timeCollector;
	        this.formulaCollector = formulaCollector;

//	        this.firstPhase = new FamilyBasedFirstPhase(modelChecker, formulaCollector);
	        this.firstPhase = new FeatureBasedFirstPhase(modelChecker,
                    formulaCollector);
	        this.helper = new FamilyBasedHelper(expressionSolver);
	        
	        derive = DerivationFunction.abstractDerivation(new IfOperator<FDTMC>(),
                    FDTMC::inline,
                    trivialFdtmc());
	}
	
	 // function to sort hashmap by values 
    public static HashMap<String, Integer> sortByValue(Map<String, Integer> dependencesSizeMap) 
    { 
        // Create a list from elements of HashMap 
        List<Map.Entry<String, Integer> > list = 
               new LinkedList<Map.Entry<String, Integer> >(dependencesSizeMap.entrySet()); 
  
        // Sort the list 
        Collections.sort(list, new Comparator<Map.Entry<String, Integer> >() { 
            public int compare(Map.Entry<String, Integer> o1,  
                               Map.Entry<String, Integer> o2) 
            { 
                return (o1.getValue()).compareTo(o2.getValue()); 
            } 
        }); 
          
        // put data from sorted list to hashmap  
        HashMap<String, Integer> temp = new LinkedHashMap<String, Integer>(); 
        for (Map.Entry<String, Integer> aa : list) { 
            temp.put(aa.getKey(), aa.getValue()); 
        } 
        return temp; 
    } 

	public IReliabilityAnalysisResults evaluateReliability(RDGNode node, ConcurrencyStrategy concurrencyStrategy, Stream<Collection<String>> configurations) throws CyclicRdgException, UnknownFeatureException {
		
		List<RDGNode> dependencies = node.getDependenciesTransitiveClosure();
		List<String> presenceConditions = dependencies.stream()
				.map(RDGNode::getPresenceCondition)
                .collect(Collectors.toList());
		
		FDTMC model = node.getFDTMC();
		 List<Component<String>> expressions = firstPhase.getReliabilityExpressions(dependencies, concurrencyStrategy);
		 
		 Map<String,String> nodeExp = new HashMap<String,String>();
		 
		 for (Component<String> expression: expressions) {
			 String[] currentAssets = expression.getAsset().split("/");
			 String finalStr = "";
			 for(int i = 0; i < currentAssets.length; i++) {				 
				 if(currentAssets[i].contains("(")) {
					 finalStr += currentAssets[i].substring(1, currentAssets[i].length() - 1) ;
					 if(i != currentAssets.length - 1) {
						 finalStr += "/";
					 }
				 } else {
					 finalStr += currentAssets[i];
				 }
			 }
			 nodeExp.put(expression.getId(), finalStr);
			 System.out.println(expression.getPresenceCondition());
		 }
		 
		 System.out.println(nodeExp);
		 
		 Map<String,Integer> dependencesSizeMap = new HashMap<String,Integer>(); 
		 
		 for (Entry<String, String> nodeCur : nodeExp.entrySet()) {
			    String[] auxExp = nodeCur.getValue().split("/");
			    Integer sizeExpSplit = 0;
			    if(auxExp[0].contains("*")) {
			    	sizeExpSplit = auxExp[0].split("\\*").length;
			    } else {
			    	sizeExpSplit = 1;
			    }
			    
			    dependencesSizeMap.put(nodeCur.getKey(), sizeExpSplit);
			}
		 
		 System.out.println(dependencesSizeMap);
		 
		 Map<String, Integer> dependencesSizeMap1 = sortByValue(dependencesSizeMap); 
		 System.out.println(dependencesSizeMap1);
//		 for (Entry<String, String> nodeCur : nodeExp.entrySet()) {
//			String id = nodeCur.getKey();
//			if(dependencesSizeMap.get(id) > 1) {
//				
//			}
////			System.out.println(dependencesSizeMap.get(id));
//		 }
		 
		
		return null;
	}

	private Double evaluateSingle(RDGNode node, Collection<String> configuration, List<RDGNode> dependencies) throws UnknownFeatureException {
        List<Component<FDTMC>> models = RDGNode.toComponentList(dependencies);
        
//        for(Component<FDTMC> model: models) {
//        	System.out.println(model.getId());
//        }
        // Lambda folding
        FDTMC rootModel = deriveFromMany(models, configuration);
        // Alpha
        String reliabilityExpression = modelChecker.getReliability(rootModel);
        formulaCollector.collectFormula(node, reliabilityExpression);
//        System.out.println(reliabilityExpression);
        // Sigma
        System.out.println(expressionSolver.solveExpression(reliabilityExpression));
        return expressionSolver.solveExpression(reliabilityExpression);
    }
	
    
    private FDTMC deriveFromMany(List<Component<FDTMC>> dependencies, Collection<String> configuration) {
        return Component.deriveFromMany(dependencies,
                                        derive,
                                        c -> PresenceConditions.isPresent(c.getPresenceCondition(),
                                                                          configuration,
                                                                          expressionSolver));
    }
    
    private FDTMC trivialFdtmc() {
        FDTMC trivial = new FDTMC();
        trivial.setVariableName("t");

        State initial = trivial.createInitialState();
        State success = trivial.createSuccessState();
        trivial.createTransition(initial, success, "", "1.0");

        return trivial;
    }


}
