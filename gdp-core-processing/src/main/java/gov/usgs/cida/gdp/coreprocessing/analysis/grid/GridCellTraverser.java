package gov.usgs.cida.gdp.coreprocessing.analysis.grid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.dataset.CoordinateAxis;
import ucar.nc2.dataset.CoordinateAxis1DTime;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDatatype;

/**
 *
 * @author tkunicki
 */
public class GridCellTraverser {

	private final static Logger LOGGER = LoggerFactory.getLogger(GridCellTraverser.class);

	// used for GridDataType.readDataSlice(...) calls.  The docs say that
	// if a dimension fails to exist the value is ignored and if it does
	// exist and the index is < 0 all data is returned.  For non-existant
	// indices we are going to pass in Integer.MAX_VALUE so that if the
	// dimension exists (i.e. we misread grid coordinate system) we force
	// an exception to be thrown...
	private final static int INVALID_INDEX = Integer.MAX_VALUE;

	private final GridDatatype gridDataType;
	
	private final MultiTimestepReader reader;

	private final GridType gridType;

	protected final int xCellCount;
	protected final int yCellCount;
	protected final int zCellCount;
	protected final int tCellCount;

	public GridCellTraverser(GridDatatype gridDatatype) {

		this.gridDataType = gridDatatype;

		GridCoordSystem gridCoordSystem = gridDatatype.getCoordinateSystem();
		
		this.reader = new MultiTimestepReader(gridDatatype);

		gridType = GridType.findGridType(gridCoordSystem);

		if (gridType == GridType.OTHER) {
			throw new IllegalArgumentException("Unable to traverse this grid type.");
		}

		CoordinateAxis xAxis = gridCoordSystem.getXHorizAxis();
		CoordinateAxis yAxis = gridCoordSystem.getYHorizAxis();
		CoordinateAxis zAxis = gridCoordSystem.getVerticalAxis();
		CoordinateAxis1DTime tAxis = gridCoordSystem.getTimeAxis1D();

		// will handle both CoordinateAxis1D or CoordinateAxis2D for x and y
		xCellCount = GridUtility.getXAxisLength(gridCoordSystem);
		yCellCount = GridUtility.getYAxisLength(gridCoordSystem);
		zCellCount = zAxis == null ? 0 : zAxis.getShape(0);
		tCellCount = tAxis == null ? 0 : tAxis.getShape(0);
	}

	public void traverse(GridCellVisitor visitor) throws IOException {
		traverse(Arrays.asList(new GridCellVisitor[] { visitor }));
	}

	public void traverse(List<GridCellVisitor> visitorList) throws IOException {
		if (gridType == GridType.OTHER) {
			throw new IllegalStateException("Unable to traverse this grid type.");
		}

		for (GridCellVisitor visitor : visitorList) {
			visitor.traverseStart(gridDataType);
		}
		
		switch (gridType) {
			case YX:
				{
					Array array = reader.readDataSlice(INVALID_INDEX, INVALID_INDEX);
					for (GridCellVisitor visitor : visitorList) {
						doTraverseXY(visitor, array);
					}
				}
				break;
			case ZYX:
				{
					for (int zCellIndex = 0; zCellIndex < zCellCount; ++zCellIndex) {
						Array array = reader.readDataSlice(INVALID_INDEX, zCellIndex);
						for (GridCellVisitor visitor : visitorList) {
							if (visitor.zStart(zCellIndex) && visitor.traverseContinue()) {
								doTraverseXY(visitor, array);
								visitor.zEnd(zCellIndex);
							}
						}
					}
				}
				break;
			case TYX:
				{
					for (int tCellIndex = 0; tCellIndex < tCellCount; ++tCellIndex) {
						Array array = reader.readDataSlice(tCellIndex, INVALID_INDEX);
						for (GridCellVisitor visitor : visitorList) {
							if (visitor.tStart(tCellIndex) && visitor.traverseContinue()) {
								doTraverseXY(visitor, array);
								visitor.tEnd(tCellIndex);
							}
						}
					}
				}
				break;
			case TZYX:
				{
					for (int tCellIndex = 0; tCellIndex < tCellCount; ++tCellIndex) {
						ArrayList<GridCellVisitor> tVisitorList = new ArrayList<>(visitorList);
						for (GridCellVisitor visitor : visitorList) {
							if (!visitor.tStart(tCellIndex) || !visitor.traverseContinue()) {
								tVisitorList.remove(visitor);
							}
						}
						if (!tVisitorList.isEmpty()) {
							for (int zCellIndex = 0; zCellIndex < zCellCount; ++zCellIndex) {
								Array array = reader.readDataSlice(tCellIndex, zCellIndex);
								for (GridCellVisitor tVisitor : tVisitorList) {
									if (tVisitor.zStart(zCellIndex) && tVisitor.traverseContinue()) {
										doTraverseXY(tVisitor, array);
										tVisitor.zEnd(zCellIndex);
									}
								}
							}
							for (GridCellVisitor tVisitor : tVisitorList) {
								tVisitor.tEnd(tCellIndex);
							}
						}
					}
				}
				break;
		}
		for (GridCellVisitor visitor : visitorList) {
			visitor.traverseEnd();
		}
	}


	protected void doTraverseXY(GridCellVisitor visitor, Array array) {
		CellValueAccessor accessor = gridDataType.hasMissingData() && !gridDataType.getVariable().getUseNaNs() ?
				new ReplaceMissingValueWithNaNAccessor(array) :
				new NoOpAccessor(array);
		Index arrayIndex = array.getIndex();
		visitor.yxStart();
		for (int yCellIndex = 0; yCellIndex < yCellCount; ++yCellIndex) {
			for (int xCellIndex = 0; xCellIndex < xCellCount && visitor.traverseContinue(); ++xCellIndex) {
				visitor.processGridCell(
						xCellIndex,
						yCellIndex,
						accessor.getDouble(arrayIndex.set(yCellIndex, xCellIndex)));
			}
		}
		visitor.yxEnd();
	}
	
	abstract class CellValueAccessor {
		protected final Array array;
		private CellValueAccessor(Array array) {
			this.array = array;
		}
		abstract double getDouble(Index index);
	}

	class NoOpAccessor extends CellValueAccessor {
		NoOpAccessor(Array array) {
			super(array);
		}
		@Override double getDouble(Index index) {
			return array.getDouble(index);
		}
	}

	class ReplaceMissingValueWithNaNAccessor extends CellValueAccessor {
		ReplaceMissingValueWithNaNAccessor(Array array) {
			super(array);
		}
		@Override double getDouble(Index index) {
			double value = array.getDouble(index);
			return gridDataType.isMissingData(value) ?
					Double.NaN : value;
		}
	}
}
