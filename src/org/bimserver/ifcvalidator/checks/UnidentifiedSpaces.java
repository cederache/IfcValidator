package org.bimserver.ifcvalidator.checks;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.ifcvalidator.Translator;
import org.bimserver.models.ifc2x3tc1.IfcBuildingElement;
import org.bimserver.models.ifc2x3tc1.IfcBuildingStorey;
import org.bimserver.models.ifc2x3tc1.IfcCurtainWall;
import org.bimserver.models.ifc2x3tc1.IfcElement;
import org.bimserver.models.ifc2x3tc1.IfcProduct;
import org.bimserver.models.ifc2x3tc1.IfcRelConnectsElements;
import org.bimserver.models.ifc2x3tc1.IfcRelConnectsPathElements;
import org.bimserver.models.ifc2x3tc1.IfcSpace;
import org.bimserver.models.ifc2x3tc1.IfcWall;
import org.bimserver.models.ifc2x3tc1.Tristate;
import org.bimserver.utils.Display;
import org.bimserver.utils.IfcTools2D;
import org.bimserver.utils.IfcUtils;
import org.bimserver.validationreport.IssueException;
import org.bimserver.validationreport.IssueInterface;
import org.jgrapht.EdgeFactory;
import org.jgrapht.graph.ClassBasedEdgeFactory;
import org.jgrapht.graph.Pseudograph;

public class UnidentifiedSpaces extends ModelCheck {

	public UnidentifiedSpaces() {
		super("SPACES", "UNIDENTIFIED");
	}

	private IfcBuildingElementWrapper getOrCreateWrapper(Map<IfcBuildingElement, IfcBuildingElementWrapper> mapping, IfcBuildingElement ifcBuildingElement) {
		IfcBuildingElementWrapper ifcBuildingElementWrapper = mapping.get(ifcBuildingElement);
		if (ifcBuildingElementWrapper == null) {
			ifcBuildingElementWrapper = new IfcBuildingElementWrapper(ifcBuildingElement);
			mapping.put(ifcBuildingElement, ifcBuildingElementWrapper);
		}
		return ifcBuildingElementWrapper;
	}
	
	@Override
	public boolean check(IfcModelInterface model, IssueInterface issueInterface, Translator translator) throws IssueException {
		Random random = new Random();
		float lengthUnitPrefix = IfcUtils.getLengthUnitPrefix(model);
		for (IfcBuildingStorey ifcBuildingStorey : model.getAll(IfcBuildingStorey.class)) {
			System.out.println(ifcBuildingStorey.getName());
			
			BufferedImage image = new BufferedImage(2000, 2000, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = (Graphics2D) image.getGraphics();
			
			AffineTransform flip = AffineTransform.getScaleInstance(-1, 1);
			flip.translate(-image.getWidth(), 0);
			graphics.transform(flip);

			graphics.setColor(Color.BLACK);
			graphics.fillRect(0, 0, 2000, 2000);
			
			Area totalArea = new Area();
			for (IfcProduct ifcProduct : IfcUtils.getDecomposition(ifcBuildingStorey)) {
				if (ifcProduct instanceof IfcSpace) {
					Area area = IfcTools2D.get2D(ifcProduct, lengthUnitPrefix);
					if (area != null) {
						totalArea.add(area);
					}
				}
			}
			EdgeFactory<IfcBuildingElementWrapper, IfcRelConnectsPathElements> factory = new ClassBasedEdgeFactory<>(IfcRelConnectsPathElements.class);
			Pseudograph<IfcBuildingElementWrapper, IfcRelConnectsPathElements> graph = new Pseudograph<>(factory);

			Map<IfcBuildingElement, IfcBuildingElementWrapper> mapping = new HashMap<>();
			
			for (IfcProduct ifcProduct : IfcUtils.getContains(ifcBuildingStorey)) {
				if (ifcProduct instanceof IfcBuildingElement) {
					IfcBuildingElement ifcBuildingElement = (IfcBuildingElement)ifcProduct;
					graph.addVertex(getOrCreateWrapper(mapping, ifcBuildingElement));
					Area area = IfcTools2D.get2D(ifcProduct, lengthUnitPrefix);
					if (area != null) {
						totalArea.add(area);
					}
				}
			}
			for (IfcProduct ifcProduct : IfcUtils.getContains(ifcBuildingStorey)) {
				if (ifcProduct instanceof IfcWall || ifcProduct instanceof IfcCurtainWall) {
					IfcElement ifcWall = ((IfcElement)ifcProduct);
					for (IfcRelConnectsElements ifcRelConnectsElements : ifcWall.getConnectedFrom()) {
						if (ifcRelConnectsElements instanceof IfcRelConnectsPathElements) {
							IfcRelConnectsPathElements ifcRelConnectsPathElements = (IfcRelConnectsPathElements)ifcRelConnectsElements;
							IfcBuildingElementWrapper wall1 = getOrCreateWrapper(mapping, (IfcBuildingElement)ifcRelConnectsPathElements.getRelatedElement());
							IfcBuildingElementWrapper wall2 = getOrCreateWrapper(mapping, (IfcBuildingElement)ifcRelConnectsPathElements.getRelatingElement());
							if (!graph.containsVertex(wall1)) {
								graph.addVertex(wall1);
							}
							if (!graph.containsVertex(wall2)) {
								graph.addVertex(wall2);
							}
							graph.addEdge(wall1, wall2, ifcRelConnectsPathElements);
						}
					}
				}
			}
			
			FindAllCyclesAlgo<IfcBuildingElementWrapper, IfcRelConnectsPathElements> algorighm = new FindAllCyclesAlgo<>(graph);
			List<Set<IfcBuildingElementWrapper>> findSimpleCycles = algorighm.findAllCycles();
			
			double scaleX = 1600 / totalArea.getBounds().getWidth();
			double scaleY = 1600 / totalArea.getBounds().getHeight();
			double scale = Math.min(scaleX, scaleY);
			
			AffineTransform affineTransform = new AffineTransform();
			affineTransform.translate(1000, 1000);
			affineTransform.scale(scale, scale);
			affineTransform.translate(-totalArea.getBounds2D().getCenterX(), -totalArea.getBounds2D().getCenterY());

			List<Set<IfcBuildingElementWrapper>> finalList = new ArrayList<>();
			
			for (Set<IfcBuildingElementWrapper> list : findSimpleCycles) {
				Area cycleArea = new Area();
				for (IfcBuildingElementWrapper ifcWallOutside : list) {
					Area areaOutside = IfcTools2D.get2D(ifcWallOutside.get(), lengthUnitPrefix);
					if (areaOutside != null) {
						cycleArea.add(areaOutside);
					}
				}
				
				Area smallest = IfcTools2D.findSmallest(cycleArea);

//				if (smallest != null) {
//					graphics.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
//					smallest.transform(affineTransform);
//					graphics.fill(smallest);
//				}

				if (smallest != null) {
					boolean foundCompleteFitting = false;
					for (Set<IfcBuildingElementWrapper> insideList : findSimpleCycles) {
						for (IfcBuildingElementWrapper ifcWallInside : insideList) {
							Area areaInside = IfcTools2D.get2D(ifcWallInside.get(), lengthUnitPrefix);
							if (areaInside != null) {
								if (IfcTools2D.containsAllPoints(smallest, areaInside)) {
									foundCompleteFitting = true;
								}
							}
						}
					}
					if (!foundCompleteFitting) {
						finalList.add(list);
					}
				}
			}

			Area checkArea = new Area();
			for (Set<IfcBuildingElementWrapper> list : finalList) {
//				boolean allExternal = true;
//				for (IfcBuildingElementWrapper ifcBuildingElement : list) {
//					Tristate booleanProperty = IfcUtils.getBooleanProperty(ifcBuildingElement.get(), "IsExternal");
//					if (booleanProperty == null || booleanProperty == Tristate.FALSE) {
//						allExternal = false;
//					}
//				}
//				if (allExternal) {
//					continue;
//				}
				Area cycleArea = new Area();
				for (IfcBuildingElementWrapper ifcWall : list) {
					Area area = IfcTools2D.get2D(ifcWall.get(), lengthUnitPrefix);
					if (area != null) {
						cycleArea.add(area);
					}
				}
				
//				graphics.setColor(Color.ORANGE);
//				cycleArea.transform(affineTransform);
//				graphics.fill(cycleArea);
				
				// Cycle area now should have an enclosed area, we should find out what the inside is
				
//				Area outerCurve = getOuterCurve(cycleArea);
//				if (outerCurve != null) {
//					outerCurve.transform(affineTransform);
//					graphics.setColor(Color.ORANGE);
//					graphics.draw(outerCurve);
//				}
//				Area innerCurve = getInnerCurve(cycleArea);
//				if (innerCurve != null) {
//					innerCurve.transform(affineTransform);
//					graphics.setColor(Color.GREEN);
//					graphics.draw(innerCurve);
//				}

				Area innerCurve = getInnerCurve(cycleArea);
				if (innerCurve != null) {
					checkArea.add(innerCurve);
					
//					graphics.setColor(new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
//					innerCurve.transform(affineTransform);
//					graphics.fill(innerCurve);
				}
				
//				PathIterator pathIterator = cycleArea.getPathIterator(null);
//				if (cycleArea.isSingular()) {
//					System.out.println("Is singular");
//				} else {
//					Path2D.Double tmp = new Path2D.Double();
//					Path2D.Double smallest = new Path2D.Double();
//					Rectangle smallestRectangle = null;
//					while (!pathIterator.isDone()) {
//						double[] coords = new double[6];
//						int type = pathIterator.currentSegment(coords);
//						if (type == 0) {
//							tmp.moveTo(coords[0], coords[1]);
//						} else if (type == 4) {
//							tmp.closePath();
//							
//							// TODO use area, not the containment of aabb's, this only sort of works for rectangular "spaces"
//							if (smallestRectangle == null || smallestRectangle.contains(tmp.getBounds())) {
//								smallestRectangle = tmp.getBounds();
//								smallest = tmp;
//							}
//							tmp = new Path2D.Double();
//						} else if (type == 1) {
//							tmp.lineTo(coords[0], coords[1]);
//						}
//						pathIterator.next();
//					}
//					if (smallest != null) {
//						Area smallestArea = new Area(smallest);
//						
//						AffineTransform aLittleSmaller = new AffineTransform();
//						double centerX = smallestArea.getBounds2D().getCenterX();
//						double centerY = smallestArea.getBounds2D().getCenterY();
//						aLittleSmaller.translate(centerX, centerY);
//						aLittleSmaller.scale(0.2, 0.2);
//						aLittleSmaller.translate(-centerX, -centerY);
//
////						smallestArea.transform(affineTransform);
////						graphics.fill(smallestArea);
//
//						smallestArea.transform(aLittleSmaller);
//						checkArea.add(smallestArea);
//					}
//				}
			}
			
			for (IfcProduct ifcProduct : IfcUtils.getDecomposition(ifcBuildingStorey)) {
				if (ifcProduct instanceof IfcSpace) {
					Area area = IfcTools2D.get2D(ifcProduct, lengthUnitPrefix);
					if (area != null) {
						checkArea.subtract(area);
					}
				}
			}
			
//			for (IfcProduct ifcProduct : IfcUtils.getContains(ifcBuildingStorey)) {
//				if (ifcProduct instanceof IfcWall) {
//					Area area = IfcTools2D.get2D(ifcProduct, lengthUnitPrefix);
//					if (area != null) {
//						totalArea.add(area);
//					}
//				}
//			}
			
			graphics.setColor(Color.BLUE);
			for (IfcProduct ifcProduct : IfcUtils.getDecomposition(ifcBuildingStorey)) {
				if (ifcProduct instanceof IfcSpace) {
					Area area = IfcTools2D.get2D(ifcProduct, lengthUnitPrefix);
					if (area != null) {
						area.transform(affineTransform);
						graphics.fill(area);
					}
				}
			}
			graphics.setColor(Color.GREEN);
			for (IfcProduct ifcProduct : IfcUtils.getContains(ifcBuildingStorey)) {
				if (ifcProduct instanceof IfcWall || ifcProduct instanceof IfcCurtainWall) {
					IfcElement ifcWall = ((IfcElement)ifcProduct);
					Area area = IfcTools2D.get2D(ifcWall, lengthUnitPrefix);
					if (area != null) {
						area.transform(affineTransform);
						graphics.fill(area);
					}
				}
			}

			graphics.setColor(Color.RED);
			checkArea.transform(affineTransform);
			graphics.fill(checkArea);
			
			Display display = new Display(ifcBuildingStorey.getName(), 2000, 2000);
			display.setImage(image);
		}
		
		return false;
	}
	
	private Area getInnerCurve(Area area) {
		PathIterator pathIterator = area.getPathIterator(null);
		if (area.isSingular()) {
			System.out.println("Is singular");
		} else {
			Path2D.Double tmp = new Path2D.Double();
			Path2D.Double smallest = new Path2D.Double();
			Rectangle smallestRectangle = null;
			while (!pathIterator.isDone()) {
				double[] coords = new double[6];
				int type = pathIterator.currentSegment(coords);
				if (type == 0) {
					tmp.moveTo(coords[0], coords[1]);
				} else if (type == 4) {
					tmp.closePath();
					
					// TODO use area, not the containment of aabb's, this only sort of works for rectangular "spaces"
					if (smallestRectangle == null || smallestRectangle.contains(tmp.getBounds())) {
						smallestRectangle = tmp.getBounds();
						smallest = tmp;
					}
					tmp = new Path2D.Double();
				} else if (type == 1) {
					tmp.lineTo(coords[0], coords[1]);
				}
				pathIterator.next();
			}
			if (smallest != null) {
				Area smallestArea = new Area(smallest);
				return smallestArea;
			}
		}
		return null;
	}
	
	private Area getOuterCurve(Area area) {
		PathIterator pathIterator = area.getPathIterator(null);
		if (area.isSingular()) {
			System.out.println("Is singular");
		} else {
			Path2D.Double tmp = new Path2D.Double();
			Path2D.Double largest = new Path2D.Double();
			Rectangle largestRectangle = null;
			while (!pathIterator.isDone()) {
				double[] coords = new double[6];
				int type = pathIterator.currentSegment(coords);
				if (type == 0) {
					tmp.moveTo(coords[0], coords[1]);
				} else if (type == 4) {
					tmp.closePath();
					
					// TODO use area, not the containment of aabb's, this only sort of works for rectangular "spaces"
					if (largestRectangle == null || tmp.getBounds().contains(largestRectangle)) {
						largestRectangle = tmp.getBounds();
						largest = tmp;
					}
					tmp = new Path2D.Double();
				} else if (type == 1) {
					tmp.lineTo(coords[0], coords[1]);
				}
				pathIterator.next();
			}
			if (largest != null) {
				return new Area(largest);
			}
		}
		return null;
	}

}