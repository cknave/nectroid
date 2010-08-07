#!/usr/bin/env python
#
# This file is part of Nectroid.
#
# Nectroid is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Nectroid is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with Nectroid.  If not, see <http://www.gnu.org/licenses/>.
"""Test color shifter for the Nectroid background."""

import colorsys

import gtk


#
# Constants
#

NORMAL_COLOR = gtk.gdk.color_parse('#F37502')

WAFFLE_IMG = \
    "GdkP" \
    "\0\0\0H" \
    "\1\1\0\1" \
    "\0\0\0\14" \
    "\0\0\0\4" \
    "\0\0\0\4" \
    "\376\230\0\377\232\0\377\233\0\377\232\0\377\232\0\345i\0\327N\0\352" \
    "a\0\377\233\0\340P\0\3268\0\347J\4\377\232\0\376h\0\375Q\4\375p\33"


#
# Color shifter algorithm
#

class Shifter:
    def __init__(self, src_color=NORMAL_COLOR):
        # Get HSV value of src_color.
        self.h_src = src_color.hue
        self.s_src = src_color.saturation
        self.v_src = src_color.value

    def apply(self, src, dest):
        """Apply the color shift from the src Image to the dest Image."""
        src_mtx = src.get_pixels_array()
        dest_mtx = dest.get_pixels_array()
        num_rows, num_cols = src_mtx.shape[:2]
        for y in range(num_rows):
            for x in range(num_cols):
                # Get r,g,b values of each pixel in [0,1].
                r_in, g_in, b_in = (src_mtx[y][x] / 255.0)
                h_in, s_in, v_in = colorsys.rgb_to_hsv(r_in, g_in, b_in)
                # Shift this color and write it to dest.
                h_out, s_out, v_out = self.shift_hsv(h_in, s_in, v_in)
                r_out, g_out, b_out = colorsys.hsv_to_rgb(h_out, s_out, v_out)
                dest_mtx[y][x][0] = int(r_out * 255.0)
                dest_mtx[y][x][1] = int(g_out * 255.0)
                dest_mtx[y][x][2] = int(b_out * 255.0)

    def set_shift_for_color(self, color):
        """Set the color shift values for this target gtk.gdk.Color object."""
        self.set_shift_for_hsv(color.hue, color.saturation, color.value)

    def set_shift_for_hsv(self, h_in, s_in, v_in):
        """Set the color shift values for this target color in HSV."""
        self.h_rot = h_in - self.h_src
        self.s_coeff = s_in / self.s_src
        self.v_coeff = v_in / self.v_src

    def shift_hsv(self, h_in, s_in, v_in):
        """Return shifted (h, s, v) values for the input HSV values."""
        v_out = v_in * self.v_coeff
        s_out = s_in * self.s_coeff
        h_out = h_in + self.h_rot
        if h_out < 0.0:
            h_out += 1.0
        elif h_out > 1.0:
            h_out -= 1.0
        return (h_out, s_out, v_out)


#
# Image utilities
#

def create_waffle_pixbuf():
    """Return a GDK Pixbuf of the waffle image."""
    size = len(WAFFLE_IMG)
    pixbuf = gtk.gdk.pixbuf_new_from_inline(size, WAFFLE_IMG, False)
    return pixbuf


#
# App class
#

class ColorShifterApp:
    def __init__(self):
        self.shifter = Shifter()
        self.waffle_pixbuf = create_waffle_pixbuf()
        self.working_pixbuf = self.waffle_pixbuf.copy()
        # waffle_pixmap will be updated once its draw_area is realized, and
        # whenever the color is changed:
        self.waffle_pixmap = gtk.gdk.Pixmap(None,
                self.waffle_pixbuf.get_width(),
                self.waffle_pixbuf.get_height(), 24)
        self._create_gui()
        self._link_gui()
        self._fill_gui_defaults()

    def start(self):
        """Start the GTK+ main loop."""
        self.window.show_all()
        gtk.main()

    def on_color_changed(self, *args):
        """The user picked a new color; adjust the color on screen."""
        new_color = self.color_picker.get_current_color()
        self.shifter.set_shift_for_color(new_color)
        self.shifter.apply(self.waffle_pixbuf, self.working_pixbuf)
        self._render_waffle_pixmap()

    def on_destroy(self, *args):
        """The user closed the window."""
        gtk.main_quit()

    def on_draw_area_realized(self, *args):
        """The drawing area was realized; fill it with waffle!"""
        self._render_waffle_pixmap()
        self.draw_area.window.set_back_pixmap(self.waffle_pixmap, False)

    def _create_gui(self):
        """Create all GUI widgets."""
        self.window = gtk.Window()
        self.draw_area = gtk.DrawingArea()
        self.draw_area.set_size_request(100, 100)
        self.color_picker = gtk.ColorSelection()
        color_frame = gtk.AspectFrame()
        color_frame.add(self.color_picker)
        hbox = gtk.HBox()
        hbox.pack_start(self.draw_area, expand=True, fill=True, padding=4)
        hbox.pack_end(color_frame, expand=False, fill=True, padding=4)
        self.window.add(hbox)

    def _fill_gui_defaults(self):
        """Fill in default values in the GUI."""
        self.color_picker.set_current_color(NORMAL_COLOR)

    def _link_gui(self):
        """Link GUI items to event handlers."""
        self.window.connect('destroy', self.on_destroy)
        self.draw_area.connect_after('realize', self.on_draw_area_realized)
        self.color_picker.connect('color-changed', self.on_color_changed)

    def _render_waffle_pixmap(self):
        gc = self.window.style.fg_gc[gtk.STATE_NORMAL]
        self.waffle_pixmap.draw_pixbuf(gc, self.working_pixbuf, 0, 0, 0, 0)
        self.draw_area.queue_draw()


#
# Main function
#

def main():
    app = ColorShifterApp()
    app.start()

if __name__ == '__main__':
    main()
