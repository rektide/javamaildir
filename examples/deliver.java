/*
 * JavaMaildir example deliver - a simple example of javamaildir usage
 * Copyright (C) 2002 Alexander Zhukov (zhukov@ukrpost.net)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
//20021230
package examples;

import java.io.*;
import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

import org.apache.log4j.*;

public class deliver
{
	private static Logger log = Logger.getLogger(deliver.class);
	public static void main(String argv[]) 
		throws Exception
	{
		BasicConfigurator.configure();
		String maildirpath = "///Maildir/";
		if ( argv.length == 0 ) {
			System.err.println("usage: deliver filename [maildirpath]");
			System.exit(1);
		} else if ( argv.length >= 2 ) {
			if ( argv[1].startsWith("/") )
				maildirpath = argv[1];
			else
				maildirpath = "///" + argv[1];
		}

		log.info("Delivering " + argv[0] + " to " + maildirpath);

		Properties props = new Properties();
		//the following specifies whether to create maildirpath if it is not existant
		//if not specified then autocreatedir is false
		props.put("mail.store.maildir.autocreatedir", "true"); 
		Session session = Session.getInstance(props, null);
		session.setDebug(true);

		Store store = session.getStore(new URLName("maildir:"+maildirpath));
		Folder inbox = store.getFolder("inbox");
		inbox.open(Folder.READ_WRITE);
		MimeMessage mm = new MimeMessage(session, new FileInputStream(argv[0]));
		inbox.appendMessages(new Message[]{(Message)mm});
        inbox.close(true);
		//MaildirStore mstore = (MaildirStore)store;
		//System.out.println(((mstore.getQuota(""))[0]).toString());
	}
}
