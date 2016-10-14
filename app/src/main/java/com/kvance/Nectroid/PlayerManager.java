// This file is part of Nectroid.
//
// Nectroid is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Nectroid is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Nectroid.  If not, see <http://www.gnu.org/licenses/>.

package com.kvance.Nectroid;

import java.util.HashSet;


class PlayerManager
{
    public interface StateListener {
        abstract void onStateChanged(PlayerService.State newState);
    }


    private PlayerService mPlayer;
    private PlayerService.State mPlayerState;

    private HashSet<StateListener> mStateListeners;


    public PlayerManager()
    {
        mStateListeners = new HashSet<StateListener>();
        mPlayerState = PlayerService.State.STOPPED;
    }


    ///
    /// Getters
    ///

    public PlayerService getPlayer() { return mPlayer; }
    public PlayerService.State getPlayerState() { return mPlayerState; }


    ///
    /// Setters
    ///

    public void setPlayer(PlayerService player)
    {
        mPlayer = player;
        if(player == null) {
            // If the player was in the loading or playing state, switch to stopped.
            if(mPlayerState == PlayerService.State.LOADING ||
                    mPlayerState == PlayerService.State.PLAYING) {
                mPlayerState = PlayerService.State.STOPPED;
                notifyStateChanged();
            }
        }
    }

    public void setPlayerState(PlayerService.State state)
    {
        mPlayerState = state;
        notifyStateChanged();
    }


    ///
    /// Public interface
    ///

    public boolean isPlaying()
    {
        return (mPlayerState == PlayerService.State.PLAYING);
    }


    public void addStateListener(StateListener listener)
    {
        mStateListeners.add(listener);
    }

    public void removeStateListener(StateListener listener)
    {
        mStateListeners.remove(listener);
    }


    ///
    /// Utility methods
    ///

    private void notifyStateChanged()
    {
        for(StateListener listener : mStateListeners) {
            listener.onStateChanged(mPlayerState);
        }
    }
}
