package com.intelligence.smscounter.activity

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.intelligence.smscounter.R
import com.intelligence.smscounter.adapter.ContactsAdapter
import com.intelligence.smscounter.model.Contact
import com.intelligence.smscounter.model.SMSData
import com.intelligence.smscounter.util.Splitter
import com.intelligence.smscounter.util.Splitter.Companion.splitMessage
import kotlinx.android.synthetic.main.activity_contacts_s_m_s.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.ceil


class ContactsSMSActivity : AppCompatActivity() {

    private val smsList = ArrayList<SMSData>()
    private val contactList = ArrayList<Contact>()
    private lateinit var savedContacts: ArrayList<Contact>
    private var senderNameOrPhone: String = "MPESA"
    private var requestReadContacts = 998
    private var keyword: String = ""
    private lateinit var adapter: ContactsAdapter
    private val tag = "ContactsSMSActivity"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts_s_m_s)

        supportActionBar!!.setTitle("Unsaved Contacts")
        supportActionBar!!.setSubtitle("from your M-PESA transactions")
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        /*val mContact = mContact("Joseph Ndung'u Noblepal", "0726266668")
        mContact.saveContact(this, mContact)*/

        //adapter = MessagesAdapter(smsList, this)
        savedContacts = ArrayList()

    }

    private fun readMessages(urlString: String, selection: String?): Boolean {
        smsList.clear()
        contactList.clear()
        val isSuccessful: Boolean
        var count = 0
        var inc = 0.0
        var prog = ""

        val cursor = contentResolver.query(
                Uri.parse("content://sms/$urlString"),
                null,
                selection,
                null,
                null
        )

        count = cursor!!.count
        inc = count.toDouble()

        if (cursor.moveToFirst()) {
            val nameID = cursor.getColumnIndex("address")
            val messageID = cursor.getColumnIndex("body")
            val dateID = cursor.getColumnIndex("date")
            var isInList: Boolean

            do {
                val percent = ((count - inc) / count) * 100.0
                Log.e("tag1 count: ", "$count")
                Log.e("tag1 inc: ", "$inc")
                Log.e("tag1 progress: ", "$percent")
                runOnUiThread {
                    prog = "Updating list ${(count - inc).toInt()}/$count)"
                    tvInfo.text = prog
                    pbLoading.progress = ceil(percent).toInt()
                }
                inc--
                isInList = false
                if (cursor.getString(messageID).toLowerCase().contains(keyword.toLowerCase()) && cursor.getString(messageID).toLowerCase().contains(Splitter.sent_to)) {
                    val dateString = cursor.getString(dateID)
                    smsList.add(SMSData(cursor.getString(nameID),
                            Date(dateString.toLong()).toString(),
                            cursor.getString(messageID).trim())
                    )

                    val nameAndPhone = splitMessage(cursor.getString(messageID).trim())
                    Log.e("setSmsMessages", "NAME_PHONE: $nameAndPhone")
                    val newContact = Contact(nameAndPhone[0], nameAndPhone[1], false)

                    for (contact in savedContacts) {
                        contact.phone = contact.phone.replace("\\s+".toRegex(), "")
                        Log.e("REMOVE white space: ", contact.phone)
                        if (contact.phone.startsWith("+254")) {
                            contact.phone = contact.phone.replace("+254", "0")
                        }
                        if (contact.phone == newContact.phone) newContact.isSaved = true
                    }
                    for (nContact in contactList) {
                        if (nContact.contactName.equals(newContact.contactName)) {
                            Log.e("CONFLICT: ", "${nContact.contactName} already in list. Skipping")
                            isInList = true
                            break
                        }
                    }
                    if (!isInList) {
                        if (!newContact.phone.equals(""))
                            contactList.add(newContact)
                    }

                }
            } while (cursor.moveToNext())
            isSuccessful = true
        } else {
            isSuccessful = false
        }

        cursor.close()

        return isSuccessful
    }

    private fun updateUI(success: Boolean) {
        pbLoading.visibility = View.GONE
        tvInfo.visibility = View.GONE
        if (success) {
            adapter.notifyDataSetChanged()
        } else {
            Log.e(tag, "updateUI: false")
        }

    }

    private fun getAllContacts(): ArrayList<Contact> {
        val cList = ArrayList<Contact>()
        var phoneNo = ""
        var name: String
        val cr = contentResolver
        var count = 0
        var inc = 0.0
        var prog = ""

        val cur = cr.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)
        if (cur != null && cur.count > 0) {

            count = cur.count
            inc = count.toDouble()
            doAsync {
                while (cur.moveToNext()) {
                    val percent = ((count - inc) / count) * 100.0
                    Log.e("tag count: ", "$count")
                    Log.e("tag inc: ", "$inc")
                    Log.e("tag progress: ", "$percent")
                    uiThread {
                        prog = "Reading contacts ${(count - inc).toInt()}/$count"
                        tvInfo.text = prog
                        pbLoading.progress = ceil(percent).toInt()
                    }
                    val id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID))
                    name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                    //nameList.add(name)
                    if (cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                        val pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                arrayOf(id), null)
                        while (pCur!!.moveToNext()) {
                            phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                        }
                        pCur.close()

                        val contact = Contact(name, phoneNo, true)

                        cList.add(contact)

                    }
                    inc--
                }
                cur.close()
                uiThread {
                    doAsync {
                        val successOrFail = readMessages("", "address LIKE '$senderNameOrPhone'")

                        uiThread {
                            updateUI(successOrFail)
                        }
                    }
                }
            }
        }
        return cList
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
                requestReadContacts)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            requestReadContacts -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    readContacts()
                } else {
                    // permission denied,Disable the
                    // functionality that depends on this permission.
                }
                return
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter = ContactsAdapter(contactList, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        adapter.addContactClickCallback { c ->
            c.saveContact(this, c)
        }
        readContacts()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_sms, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
        }
        if (item.itemId == R.id.action_refresh) {
            readContacts()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun readContacts() {
        savedContacts.clear()
        contactList.clear()
        adapter.notifyDataSetChanged()
        pbLoading.visibility = View.VISIBLE
        tvInfo.visibility = View.VISIBLE
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            savedContacts = getAllContacts()
        } else {
            requestPermission()
        }
    }

}