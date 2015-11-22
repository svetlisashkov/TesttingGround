//Събирач на боклук
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace _6_12
{
    class Program
    {
        class Destruct
        {
            public int x;
            public Destruct(int i)
            {
                x = i;
            }
            // Извиква се при освобождаване на паметта, заета от обекта. 
            ~Destruct()
            {
                Console.WriteLine("Да се унищожи обекта " + x);
            }
            // Създава обект и незабавно го унищожава. 
            public static void Generator(int i)
            {
                Destruct о = new Destruct(i);
            }
        }

        static void Main(string[] args)
        {
            int count;
            /* Пристъпва се към създаване на голям брой обекти.
            В някакъв момент се извършва "събирането на боклук".  
            За да се активира "събирача на боклук" е възможно да се             
            наложи да се увеличи броя на създаваните обекти.*/
            for (count = 1; count < 1000000; count++)
                Destruct.Generator(count);
            Console.WriteLine("Готово!");
            Console.Write("Натиснете произволен клавиш ... ");
            Console.ReadKey();
        }
    }
}
