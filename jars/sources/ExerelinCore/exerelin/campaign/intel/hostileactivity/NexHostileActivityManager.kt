package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityManager
import exerelin.utilities.NexUtilsFaction
import exerelin.utilities.StringHelper

class NexHostileActivityManager : HostileActivityManager() {

    init {
        addDefenseFleetFactorsIfNeeded(HostileActivityEventIntel.get())
    }

    override fun advance(amount: Float) {
        tracker.advance(amount)
        if (tracker.intervalElapsed()) {
            val playerHasColonies = !NexUtilsFaction.getPlayerMarkets(INCLUDE_AUTONOMOUS, false).isEmpty()
            if (HostileActivityEventIntel.get() == null && playerHasColonies) {
                val ha = HostileActivityEventIntel()
                addDefenseFleetFactorsIfNeeded(ha)
            } else if (HostileActivityEventIntel.get() != null && !playerHasColonies) {
                HostileActivityEventIntel.get().endImmediately()
            }
        }
    }

    companion object {
        const val INCLUDE_AUTONOMOUS = true // might as well since they're counted in StandardPirateActivityCause2

        @JvmStatic
        fun getString(id : String) : String {
            return StringHelper.getString("nex_hostileActivity", id, true);
        }
        @JvmStatic
        fun getString(id : String, ucFirst : Boolean) : String {
            return StringHelper.getString("nex_hostileActivity", id, ucFirst);
        }

        /*
         runcode import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
         exerelin.campaign.intel.hostileactivity.NexHostileActivityManager.addDefenseFleetFactorsIfNeeded(HostileActivityEventIntel.get())
         */
        @JvmStatic
        fun addDefenseFleetFactorsIfNeeded(ha: HostileActivityEventIntel?) {
            if (ha == null) return
            val alreadyHas = hasFactorOfClass(ha, DefenseFleetsFactor::class.java)
            if (alreadyHas) return
            var defFactor = DefenseFleetsFactor(ha);
            ha.addFactor(defFactor);
            ha.addActivity(defFactor, DefenseFleetActivityCause(ha))
            ha.addActivity(defFactor, SpecialTaskGroupActivityCause(ha))
            ha.addActivity(defFactor, MercPackageActivityCause(ha))
        }

        @JvmStatic
        fun hasFactorOfClass(ha: HostileActivityEventIntel?, factorClass: Class<*>): Boolean {
            if (ha == null) return false
            for (factor in ha.factors) {
                if (factorClass.isInstance(factor)) return true
            }
            return false
        }
    }
}