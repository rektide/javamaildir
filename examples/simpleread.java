/*
 * JavaMaildir example simpleread - a simple example of javamaildir usage
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

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

import org.apache.log4j.*;

public class simpleread {
	private static Logger log = Logger.getLogger(simpleread.class);
	public static void main(String args[]) 
		throws Exception
	{
		//set up the Logger
		BasicConfigurator.configure();

		Session session = Session.getInstance(new Properties());
		//url examples
		String user = "zhukov"; //well this is my home :) you should enter your name here
		String absolute_url = "maildir:/home/"+user+"/Maildir";
		String absolute_url2 = "maildir:////home/"+user+"/Maildir";
		String relative_url = "maildir:///testhome/Maildir";
		String url = absolute_url;
		
		Store store = session.getStore(new URLName(url));

		store.connect(); //useless with Maildir but included here for consistency
		Folder inbox = store.getFolder("inbox");
		inbox.open(Folder.READ_WRITE);
		Message m = inbox.getMessage(1);
		m.writeTo(System.out);
		System.out.println("subject of this message: " + m.getSubject());
		m.writeTo(System.out);
	}
}
