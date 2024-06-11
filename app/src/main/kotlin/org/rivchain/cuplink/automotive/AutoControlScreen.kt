package org.rivchain.cuplink.automotive

import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import org.rivchain.cuplink.CallActivity
import org.rivchain.cuplink.R
import org.rivchain.cuplink.model.Contact
import org.rivchain.cuplink.model.Contacts

@RequiresApi(Build.VERSION_CODES.M)
class AutoControlScreen(private val carContext: CarContext, private val contacts: Contacts) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val itemListBuilder = ItemList.Builder().apply {

            for (contact in contacts.contactList) {
                addItem(
                    Row.Builder()
                        .setTitle(contact.name)
                        .setImage(getCarIconFromDrawable(R.drawable.ic_contacts))
                        .setOnClickListener {
                            startCall(contact)
                        }
                        .build()
                )
            }
        }

        val listTemplateBuilder = ListTemplate.Builder().apply {
            setSingleList(itemListBuilder.build())
            setHeaderAction(Action.BACK)
        }

        return listTemplateBuilder.build()
    }

    private fun getCarIconFromDrawable(drawableResId: Int): CarIcon {
        val iconCompat = IconCompat.createWithResource(carContext, drawableResId)
        val carColor = CarColor.createCustom(Color.WHITE, Color.BLACK) // Set the tint color to white and dark color to black
        return CarIcon.Builder(iconCompat)
            .setTint(carColor)
            .build()
    }

    private fun startCall(contact: Contact) {
        val intent = CallActivity.clearTop(carContext)
        intent.action = "ACTION_OUTGOING_CALL"
        intent.putExtra("EXTRA_CONTACT", contact)
        carContext.startActivity(intent)
    }
}