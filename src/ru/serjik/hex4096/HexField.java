package ru.serjik.hex4096;

import java.util.Random;

import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;
import ru.serjik.engine.BatchDrawer;
import ru.serjik.engine.EngineView;
import ru.serjik.engine.Texture;
import ru.serjik.engine.Tile;
import ru.serjik.engine.eng;
import ru.serjik.hex4096.Cell.CellState;
import ru.serjik.utils.BitmapUtils;

public class HexField extends EngineView
{
	private static final int GEMS_PER_MOVE = 3;
	private static final Random rnd = new Random(SystemClock.elapsedRealtime());
	private static final int FIELD_SIZE = 6;

	private Cell[] cells;
	private int[] freeIndxes;
	private boolean isMoving = false;

	private Tile[] gems;
	private BatchDrawer bd;

	private float centerFieldX;
	private float centerFieldY;

	private int fieldCellsWidth;
	private float cellRadius;

	private int selectedIndex = -1;

	public HexField(Context context)
	{
		super(context);
		bd = new BatchDrawer(4096 * 4);
	}

	@Override
	public void onCreated(GL10 gl)
	{
		Texture atlas = new Texture(BitmapUtils.generate(512, 128, BitmapUtils.loadBitmapsFromAsset(eng.am), true));
		gems = Tile.split(atlas, atlas.width / 4, atlas.height / 4);
	}

	@Override
	public void onChanged(GL10 gl)
	{
		if (cells == null)
		{
			createCells();
			putGems(GEMS_PER_MOVE);
		}
	}

	@Override
	public void onDrawFrame(GL10 gl)
	{
		gl.glEnable(GL10.GL_BLEND);
		gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
		gl.glDisable(GL10.GL_DITHER);
		gl.glDisable(GL10.GL_FOG);
		gl.glDisable(GL10.GL_LIGHTING);
		gl.glDisable(GL10.GL_DEPTH_TEST);

		Texture.enable();
		Texture.filter(GL10.GL_LINEAR, GL10.GL_LINEAR);
		gl.glShadeModel(GL10.GL_SMOOTH);

		gl.glClearColor(0, 0, 0, 0);
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

		drawCells();
		
		drawGems();

		bd.flush();

		SystemClock.sleep(100);

		tick();
	}

	private boolean doTickForCells(CellState filter)
	{
		boolean result = false;
		for (Cell cell : cells)
		{
			if (cell != null)
			{
				if (cell.tick(filter))
				{
					result = true;
				}
			}
		}
		return result;
	}
	
	private void tick()
	{
		boolean isMoveEnd = isMoving;

		isMoving = doTickForCells(CellState.MOVE)?true:isMoving;
		isMoving = doTickForCells(CellState.BUMP)?true:isMoving;
		
		if (isMoveEnd && isMoving == false)
		{
			Log.v("move", "onMoveEnd!!!");
			if (putGems(GEMS_PER_MOVE) == false)
			{
				//Toast.makeText(getContext(), "GAME OVER", Toast.LENGTH_LONG).show();
				
//				for (Cell cell : cells)
//				{
//					if (cell != null)
//					{
//						cell.state = CellState.BASE;
//						cell.gem = 0;
//					}
//				}
			}
		}

	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (isMoving == false)
		{
			int action = event.getAction();
			float x = event.getX();
			float y = event.getY();

			switch (action)
			{
			case MotionEvent.ACTION_DOWN:
				selectedIndex = getIndex(x, y);
				if (selectedIndex >= 0)
				{
					return true;
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (selectedIndex >= 0)
				{
					int index = getIndex(x, y);

					if (index != selectedIndex && index >= 0)
					{
						int dx = (int) (cells[index].screenPositionX - cells[selectedIndex].screenPositionX);
						int dy = (int) (cells[index].screenPositionY - cells[selectedIndex].screenPositionY);

						if (Math.abs(dy) < Math.abs(dx) * 0.5f)
						{
							dy = 0;
							dx = dx < 0 ? -1 : 1;
						}
						else
						{
							dx = dy < 0 ? (dx < 0 ? 0 : 1) : (dx < 0 ? -1 : 0);
							dy = dy < 0 ? -1 : 1;
						}
						Log.v("move", "dx = " + dx + " dy = " + dy);

						cells[selectedIndex].bump(dx, dy);
						isMoving = true;
						return true;
					}
				}
				break;
			}
		}
		return super.onTouchEvent(event);
	}

	public boolean putGems(int count)
	{
		while (count > 0)
		{
			int freeCount = fillFreeIndexes();

			if (freeCount < 1)
			{
				return false;
			}

			cells[freeIndxes[rnd.nextInt(freeCount)]].gem = 1;

			count--;
		}

		return true;
	}

	private void drawCells()
	{
		for (Cell cell : cells)
		{
			if (cell != null)
			{
				bd.drawCentered(gems[0], cell.screenPositionX, cell.screenPositionY);
			}
		}
	}

	private void drawGems()
	{
		for (Cell cell:cells)
		{
			if (cell != null)
			{
				cell.draw(bd,gems);

				// switch (cell.state)
				// {
				// case BASE:
				// bd.drawScaledCentered(gems[1], 0.333f, cell.screenPositionX,
				// cell.screenPositionY);
				// break;
				// case START:
				// bd.drawScaledCentered(gems[2], 0.333f, cell.screenPositionX,
				// cell.screenPositionY);
				// break;
				// case MOVE:
				// bd.drawScaledCentered(gems[3], 0.333f, cell.screenPositionX,
				// cell.screenPositionY);
				// break;
				// default:
				// break;
				// }

				// if (i == selectedIndex)
				// {
				// bd.drawCentered(gems[15], cell.screenPositionX,
				// cell.screenPositionY);
				// }
			}
		}
	}

	private int fillFreeIndexes()
	{
		int count = 0;

		for (int i = 0; i < cells.length; i++)
		{
			if (cells[i] != null)
			{
				if (cells[i].gem == 0)
				{
					freeIndxes[count++] = i;
				}
			}

		}
		return count;
	}

	private void createCells()
	{
		cells = new Cell[(FIELD_SIZE * 2 - 1) * (FIELD_SIZE * 2 - 1)];
		freeIndxes = new int[cells.length];

		fillWallAndCell(FIELD_SIZE - 1, FIELD_SIZE - 1, FIELD_SIZE - 1);

		setNieghborhood();
	}

	private int getIndex(float x, float y)
	{
		for (int i = 0; i < cells.length; i++)
		{
			if (cells[i] != null)
			{
				float dx = x - cells[i].screenPositionX;
				float dy = y - cells[i].screenPositionY;
				if (dx * dx + dy * dy < cellRadius * cellRadius)
				{
					return i;
				}
			}
		}
		return -1;
	}

	private void fillWallAndCell(int cr, int cq, int dist)
	{
		fieldCellsWidth = FIELD_SIZE * 2 - 1;

		float kw = (float) (Math.sqrt(3) / 2);

		cellRadius = kw * width() / 10;

		centerFieldX = width() / 2;
		centerFieldY = height() - width() / 2;

		float cw = cellRadius * kw;
		float ch = cellRadius * 0.75f;

		for (int i = 0; i < cells.length; i++)
		{
			int ix = i % fieldCellsWidth;
			int iy = i / fieldCellsWidth;

			if (HexUtils.distance(ix, iy, cr, cq) <= dist)
			{
				int r = ix - (FIELD_SIZE - 1);
				int q = iy - (FIELD_SIZE - 1);

				// ! shit fucking trouble!!!
				// float x = cellRadius * (float) Math.sqrt(3.0f) * (q + (float)
				// r * 0.5f) + centerFieldX;
				// float y = cellRadius * 1.5f * r + centerFieldY;

				float x = r * cw * 2 + q * cw + centerFieldX;
				float y = q * ch * 2 + centerFieldY;

				if (x >= cellRadius && x < width() - cellRadius && y >= cellRadius + (height() - width())
						&& y < height() - cellRadius)
				{
					cells[i] = new Cell(x, y);
				}
			}
		}
	}

	private void setNieghborhood()
	{

		for (int i = 0; i < cells.length; i++)
		{

			if (cells[i] != null)
			{
				int ri = i % fieldCellsWidth;
				int qi = i / fieldCellsWidth;

				Cell cell = cells[i];

				cell.neighborhood[0] = cells[(ri + 1) + (qi - 1) * fieldCellsWidth];
				cell.neighborhood[1] = cells[(ri + 1) + (qi + 0) * fieldCellsWidth];
				cell.neighborhood[2] = cells[(ri + 0) + (qi + 1) * fieldCellsWidth];
				cell.neighborhood[3] = cells[(ri - 1) + (qi + 1) * fieldCellsWidth];
				cell.neighborhood[4] = cells[(ri - 1) + (qi + 0) * fieldCellsWidth];
				cell.neighborhood[5] = cells[(ri + 0) + (qi - 1) * fieldCellsWidth];
			}
		}
	}

}