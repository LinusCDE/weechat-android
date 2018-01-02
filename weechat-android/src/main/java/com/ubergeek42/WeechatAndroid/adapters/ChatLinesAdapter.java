/*******************************************************************************
 * Copyright 2012 Keith Johnson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.ubergeek42.WeechatAndroid.adapters;

import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.ubergeek42.WeechatAndroid.utils.AnimatedRecyclerView;
import com.ubergeek42.WeechatAndroid.R;
import com.ubergeek42.WeechatAndroid.WeechatActivity;
import com.ubergeek42.WeechatAndroid.relay.Buffer;
import com.ubergeek42.WeechatAndroid.relay.BufferEye;
import com.ubergeek42.WeechatAndroid.relay.Line;
import com.ubergeek42.WeechatAndroid.service.P;
import com.ubergeek42.WeechatAndroid.utils.CopyPaste;
import com.ubergeek42.WeechatAndroid.utils.Utils;
import com.ubergeek42.weechat.ColorScheme;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.ubergeek42.WeechatAndroid.R.layout.more_button;
import static com.ubergeek42.WeechatAndroid.R.layout.read_marker;
import static com.ubergeek42.WeechatAndroid.R.layout.chatview_line;

public class ChatLinesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements BufferEye {

    private static Logger logger = LoggerFactory.getLogger("ChatLinesAdapter");
    final private static boolean DEBUG = true;

    private WeechatActivity activity = null;
    private AnimatedRecyclerView uiLines;
    private @Nullable Buffer buffer;
    private List<Line> lines = new ArrayList<>();
    private List<Line> _lines = new ArrayList<>();

    private int style = 0;

    public ChatLinesAdapter(WeechatActivity activity, AnimatedRecyclerView animatedRecyclerView) {
        if (DEBUG) logger.debug("ChatLinesAdapter()");
        this.activity = activity;
        this.uiLines = animatedRecyclerView;
        setHasStableIds(true);
    }

    @UiThread public void setBuffer(@Nullable Buffer buffer) {
        this.buffer = buffer;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// row
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Row extends RecyclerView.ViewHolder {
        private TextView textView;
        private int style = -1;

        Row(View view) {
            super(view);
            textView = (TextView) view;
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            textView.setOnLongClickListener(CopyPaste.copyPaste);
        }

        void update(Line line, int newStyle) {
            textView.setTag(line);
            textView.setText(line.spannable);
            if (style != (style = newStyle)) updateStyle(textView);
        }
    }

    private static void updateStyle(TextView textView) {
        textView.setTextSize(P.textSize);
        textView.setTypeface(P.typeface);
        textView.setTextColor(0xFF000000 | ColorScheme.get().defaul[0]);
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// read marker
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class ReadMarkerRow extends RecyclerView.ViewHolder {
        private View view;
        private int style = -1;

        ReadMarkerRow(View view) {
            super(view);
            this.view = view;
        }

        void update(int newStyle) {
            if (style != (style = newStyle))
                view.setBackgroundColor(0xFF000000 | ColorScheme.get().chat_read_marker[0]);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// header
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static class Header extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView title;
        private Button button;
        private ChatLinesAdapter adapter;
        private Buffer.LINES status = Buffer.LINES.CAN_FETCH_MORE;
        private Spannable topicText = null;
        private int style = -1;

        Header(View header, ChatLinesAdapter adapter) {
            super(header);
            this.adapter = adapter;
            title = (TextView) header.findViewById(R.id.title);
            title.setMovementMethod(LinkMovementMethod.getInstance());
            title.setOnLongClickListener(CopyPaste.copyPaste);
            button = (Button) header.findViewById(R.id.button_more);
            button.setOnClickListener(this);
        }

        void update(int newStyle) {
            if (adapter.buffer == null) return;
            updateButton();
            updateTitle(newStyle);
        }

        private void updateButton() {
            if (adapter.buffer == null) return;
            final Buffer.LINES s = adapter.buffer.getLineStatus();
            if (status == s) return;
            status = s;
            if (s == Buffer.LINES.EVERYTHING_FETCHED) {
                button.setVisibility(View.GONE);
            } else {
                button.setVisibility(View.VISIBLE);
                boolean more = s == Buffer.LINES.CAN_FETCH_MORE;
                button.setEnabled(more);
                button.setTextColor(more ? 0xff80cbc4 : 0xff777777);
                button.setText(button.getContext().getString(more ? R.string.more_button : R.string.more_button_fetching));
            }
        }

        private void updateTitle(int newStyle) {
            if (adapter.buffer == null) return;
            Spannable titleSpannable = adapter.buffer.titleSpannable;
            Line titleLine = adapter.buffer.titleLine;
            if (TextUtils.isEmpty(titleSpannable) || !adapter.buffer.holdsAllLines) {
                title.setVisibility(View.GONE);
                return;
            }
            title.setVisibility(View.VISIBLE);
            Utils.setBottomMargin(title, button.getVisibility() == View.GONE ? (int) P._4dp : 0);
            if (topicText != titleSpannable) {
                title.setText(topicText = titleSpannable);
                title.setTag(titleLine);
            }
            if (style != (style = newStyle)) updateStyle(title);
        }

        @Override public void onClick(View v) {
            if (adapter.buffer == null) return;
            adapter.buffer.requestMoreLines();
            updateButton();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// RecyclerView.Adapter methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    final private static int HEADER_TYPE = -1, LINE_TYPE = 0, MARKER_TYPE = 1;
    final private static int HEADER_POINTER = -123, MARKER_POINTER = -456;

    final private static Line HEADER = new Line(HEADER_POINTER, null, null, null, false, false, null);
    final private static Line MARKER = new Line(MARKER_POINTER, null, null, null, false, false, null);

    @Override public int getItemViewType(int position) {
        //logger.trace("getItemViewType({})", position);
        long pointer = lines.get(position).pointer;
        if (pointer == HEADER_POINTER) return HEADER_TYPE;
        if (pointer == MARKER_POINTER) return MARKER_TYPE;
        return LINE_TYPE;
    }

    @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        //logger.trace("onCreateViewHolder({})", viewType);
        LayoutInflater i = LayoutInflater.from(parent.getContext());
        if (viewType == HEADER_TYPE) return new Header(i.inflate(more_button, parent, false), this);
        else if (viewType == MARKER_TYPE) return new ReadMarkerRow(i.inflate(read_marker, parent, false));
        else return new Row(i.inflate(chatview_line, parent, false));
    }

    @Override public synchronized void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        //logger.trace("onBindViewHolder(..., {}, {})", position);
        long pointer = lines.get(position).pointer;
        if (pointer == HEADER_POINTER) ((Header) holder).update(style);
        else if (pointer == MARKER_POINTER) ((ReadMarkerRow) holder).update(style);
        else ((Row) holder).update(lines.get(position), style);
    }

    @Override public int getItemCount() {
        //logger.trace("getItemCount()");
        return lines.size();
    }

    @Override public long getItemId(int position) {
        //logger.trace("getItemId({}) -> {}", position, lines.get(position).pointer);
        return lines.get(position).pointer;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // get new lines, perform a simple diff and dispatch change notifications to RecyclerView
    // this might be called by multiple threads in rapid succession
    // in case non-main thread calls this before the Runnable that sets `lines` is executed,
    // store the new list in `_lines` so that we can produce a proper diff
    @UiThread @WorkerThread private synchronized void onLinesChanged() {
        if (buffer == null) return;
        final ArrayList<Line> newLines;
        final int marker;

        synchronized (buffer) {
            newLines = new ArrayList<>(Arrays.asList(buffer.getLinesCopy()));
            marker = buffer.getReadMarkerPosition();
        }
        if (marker > 0) newLines.add(marker, MARKER);
        newLines.add(0, HEADER);

        final boolean hack = _lines.size() == 1 && newLines.size() > 1;

        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffCallback(_lines, newLines), false);
        _lines = newLines;

        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                lines = newLines;
                diffResult.dispatchUpdatesTo(ChatLinesAdapter.this);
                if (uiLines.getOnBottom()) {
                    if (hack) uiLines.scrollToPosition(getItemCount() - 1);
                    else uiLines.smoothScrollToPosition(getItemCount() - 1);
                }
                else uiLines.flashScrollbar();
                uiLines.scheduleAnimationRestoring();
            }
        });
    }

    @UiThread synchronized public void moveReadMarkerToEnd() {
        if (buffer == null) return;
        buffer.moveReadMarkerToEnd();
        onLinesChanged();
    }

    private void updateHeader() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                notifyItemChanged(0);
            }
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// BufferEye
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // increasing `style` will make all ViewHolders update visual characteristics
    @Override @UiThread public synchronized void onGlobalPreferencesChanged(boolean numberChanged) {
        if (numberChanged && buffer != null) {
            onLinesChanged();
        } else {
            style++;
            notifyItemRangeChanged(0, _lines.size());
        }
    }

    @Override public void onLinesListed() {
        onLinesChanged();
        updateHeader();
    }

    @Override public void onLineAdded() {
        onLinesChanged();
    }

    @Override public void onPropertiesChanged() {
        updateHeader();
    }

    @Override public void onBufferClosed() {}

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @UiThread public synchronized void loadLinesWithoutAnimation() {
        if (buffer == null) return;
        uiLines.disableAnimationForNextUpdate();
        onLinesChanged();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////// find hot line
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @UiThread @WorkerThread public void scrollToHotLineIfNeeded() {
        final int idx = findHotLine();
        if (idx == HOT_LINE_NOT_READY || idx == HOT_LINE_NOT_PRESENT) return;
        highlights = privates = 0;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (idx == HOT_LINE_LOST) Toast.makeText(activity, activity.getString(R.string.autoscroll_no_line), Toast.LENGTH_SHORT).show();
                else uiLines.smoothScrollToPositionAfterAnimation(idx);
            }
        });
    }

    @UiThread public synchronized void storeHotLineInfo() {
        Assert.assertNotNull(buffer);
        highlights = buffer.highlights;
        privates = (buffer.type == Buffer.PRIVATE) ? buffer.unreads : 0;
    }

    private int highlights;
    private int privates;

    private final static int HOT_LINE_LOST = -1;
    private final static int HOT_LINE_NOT_READY = -2;
    private final static int HOT_LINE_NOT_PRESENT = -3;

    synchronized private int findHotLine() {
        Assert.assertNotNull(buffer);
        if (!buffer.isWatched || !buffer.holdsAllLines) return HOT_LINE_NOT_READY;
        if ((highlights | privates) == 0) return HOT_LINE_NOT_PRESENT;

        int count = _lines.size(), idx = -1;

        if (privates > 0) {
            for (idx = count - 1; idx >= 0; idx--) {
                Line line = _lines.get(idx);
                if (line.type == Line.LINE_MESSAGE && --privates == 0) break;
            }
        } else if (highlights > 0) {
            for (idx = count - 1; idx >= 0; idx--) {
                Line line = _lines.get(idx);
                if (line.highlighted && --highlights == 0) break;
            }
        }
        return (idx < 0) ? HOT_LINE_LOST : idx + 1;
    }

    private class DiffCallback extends DiffUtil.Callback {
        private List<Line> oldLines, newLines;

        DiffCallback(List<Line> oldLines, List<Line> newLines) {
            this.oldLines = oldLines;
            this.newLines = newLines;
        }

        @Override public int getOldListSize() {
            return oldLines.size();
        }

        @Override public int getNewListSize() {
            return newLines.size();
        }

        @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldLines.get(oldItemPosition).pointer == newLines.get(newItemPosition).pointer;
        }

        @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return areItemsTheSame(oldItemPosition, newItemPosition);
        }
    }
}